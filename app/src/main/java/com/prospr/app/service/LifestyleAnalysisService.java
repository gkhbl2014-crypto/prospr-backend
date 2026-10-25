package com.prospr.app.service;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.prospr.app.entity.LifestyleInsight;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberLifestyleStatus;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.LifestyleInsightRepository;
import com.prospr.app.repository.MemberLifestyleStatusRepository;
import com.prospr.app.repository.TransactionRepository;
import com.prospr.app.service.cache.AnalyticsCacheService;

/**
 * Top-level orchestrator for the Lifestyle Creep feature. The pipeline stages (categorization,
 * monthly summary, baseline, insight generation) never see Setu types, only {@link Transaction}
 * entities already normalized and saved by the existing Setu session flow.
 *
 * V1 note: {@link #enable(Member)} deliberately does NOT create a new Setu consent. For the
 * current mock-user/sandbox setup, the transaction history needed for analysis is already sitting
 * in the database from earlier consent flows, so "enabling" lifestyle analysis just means running
 * the pipeline against what's already stored. If a real "fetch 3 more months of history on enable"
 * flow is needed later, it should call SetuConsentService the same way every other consent in this
 * app does - nothing here prevents that, it's just not what V1 does.
 */
@Service
public class LifestyleAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(LifestyleAnalysisService.class);

    private final MemberLifestyleStatusRepository statusRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionCategorizationService categorizationService;
    private final MonthlySpendingSummaryService monthlySummaryService;
    private final LifestyleBaselineService baselineService;
    private final RecurringExpenseDetectionService recurringExpenseDetectionService;
    private final LifestyleInsightService insightService;
    private final LifestyleInsightRepository insightRepository;
    private final AnalyticsCacheService analyticsCacheService;
    private final CurrentMonthResolver currentMonthResolver;

    public LifestyleAnalysisService(MemberLifestyleStatusRepository statusRepository,
                                     TransactionRepository transactionRepository,
                                     TransactionCategorizationService categorizationService,
                                     MonthlySpendingSummaryService monthlySummaryService,
                                     LifestyleBaselineService baselineService,
                                     RecurringExpenseDetectionService recurringExpenseDetectionService,
                                     LifestyleInsightService insightService,
                                     LifestyleInsightRepository insightRepository,
                                     AnalyticsCacheService analyticsCacheService,
                                     CurrentMonthResolver currentMonthResolver) {
        this.statusRepository = statusRepository;
        this.transactionRepository = transactionRepository;
        this.categorizationService = categorizationService;
        this.monthlySummaryService = monthlySummaryService;
        this.baselineService = baselineService;
        this.recurringExpenseDetectionService = recurringExpenseDetectionService;
        this.insightService = insightService;
        this.insightRepository = insightRepository;
        this.analyticsCacheService = analyticsCacheService;
        this.currentMonthResolver = currentMonthResolver;
    }

    /**
     * Runs the pipeline against whatever transaction history already exists for this member. When
     * {@link #refreshAfterDataChange} has already produced this month's summary during import, there
     * is nothing left to calculate - enabling just records the READY status, so the click is instant.
     */
    public void enable(Member member) {
        if (statusRepository.findByMemberId(member.getId()).isEmpty() && getCurrentMonthSummary(member).isPresent()) {
            MemberLifestyleStatus status = MemberLifestyleStatus.builder()
                    .member(member).status(LifestyleStatus.NOT_ENABLED).build();
            updateStatus(status, LifestyleStatus.READY);
            analyticsCacheService.evictLifestyleCreep(member.getId());
            return;
        }
        recomputeForMember(member);
    }

    public String getStatus(Member member) {
        return statusRepository.findByMemberId(member.getId())
                .map(MemberLifestyleStatus::getStatus)
                .orElse(LifestyleStatus.NOT_ENABLED);
    }

    /** Exact-key lookup (not a status-based query) so a legacy per-category "CATEGORY_SPIKE" row from
     *  before this redesign can never be picked up alongside the new overall summary. */
    public Optional<LifestyleInsight> getCurrentMonthSummary(Member member) {
        YearMonth month = currentMonthResolver.resolve(member.getId()).month();
        return insightRepository.findByMemberIdAndYearAndMonthAndCategoryAndInsightType(
                member.getId(), month.getYear(), month.getMonthValue(),
                LifestyleInsightService.CATEGORY_OVERALL, LifestyleInsightService.INSIGHT_TYPE_SUMMARY);
    }

    /**
     * Called by the analysis orchestrator after every import or data change. Members who already
     * enabled lifestyle analysis get the full status-tracked run; everyone else gets a silent
     * precompute so the results are waiting when they press Enable. Never throws, and never creates
     * a status row - a member who hasn't enabled the feature still reads as NOT_ENABLED.
     *
     * <p>Skips categorization and recurring-expense detection: the orchestrator has already run both
     * moments earlier in the same pipeline.
     */
    public void refreshAfterDataChange(Member member) {
        if (statusRepository.findByMemberId(member.getId()).isPresent()) {
            recomputeForMember(member);
            return;
        }
        try {
            monthlySummaryService.recomputeForMember(member);
            CurrentMonthResolver.EffectivePeriod period = currentMonthResolver.resolve(member.getId());
            LifestyleBaselineService.Result baselineResult = baselineService.recomputeForMember(member, period.month());
            if (baselineResult != LifestyleBaselineService.Result.INSUFFICIENT_HISTORY) {
                insightService.recomputeForMember(member, period.month(), period.asOfDate());
            }
            analyticsCacheService.evictLifestyleCreep(member.getId());
        } catch (Exception ex) {
            log.warn("Lifestyle precompute failed for member '{}': {}", member.getId(), ex.getMessage(), ex);
        }
    }

    /**
     * Runs the full pipeline for one member: categorize -> monthly summary -> baseline -> insights.
     * Never throws - any failure is recorded as ERROR status so callers (including the Setu session
     * hook) are never disrupted by a lifestyle-analysis failure.
     */
    public void recomputeForMember(Member member) {
        MemberLifestyleStatus status = statusRepository.findByMemberId(member.getId())
                .orElseGet(() -> MemberLifestyleStatus.builder().member(member).status(LifestyleStatus.NOT_ENABLED).build());
        try {
            updateStatus(status, LifestyleStatus.FETCHING_HISTORY);

            updateStatus(status, LifestyleStatus.CATEGORIZING);
            List<Transaction> uncategorized = transactionRepository.findByMemberIdAndCategoryIsNull(member.getId());
            categorizationService.categorize(uncategorized);
            transactionRepository.saveAll(uncategorized);

            updateStatus(status, LifestyleStatus.BUILDING_BASELINE);
            monthlySummaryService.recomputeForMember(member);
            CurrentMonthResolver.EffectivePeriod period = currentMonthResolver.resolve(member.getId());
            LifestyleBaselineService.Result baselineResult = baselineService.recomputeForMember(member, period.month());

            if (baselineResult == LifestyleBaselineService.Result.INSUFFICIENT_HISTORY) {
                updateStatus(status, LifestyleStatus.INSUFFICIENT_HISTORY);
                return;
            }

            // Freshens RecurringExpense rows (including firstSeenDate) so the "new recurring
            // discretionary expense" signal in the summary is never stale - this pipeline is the only
            // trigger path that doesn't already run through FinancialAnalysisOrchestratorService.
            recurringExpenseDetectionService.recomputeForMember(member);
            insightService.recomputeForMember(member, period.month(), period.asOfDate());
            updateStatus(status, LifestyleStatus.READY);
            analyticsCacheService.evictLifestyleCreep(member.getId());
        } catch (Exception ex) {
            log.error("Lifestyle analysis failed for member '{}': {}", member.getId(), ex.getMessage(), ex);
            status.setStatus(LifestyleStatus.ERROR);
            status.setLastError(ex.getMessage());
            statusRepository.save(status);
        }
    }

    private void updateStatus(MemberLifestyleStatus status, String value) {
        status.setStatus(value);
        status.setLastError(null);
        statusRepository.save(status);
    }
}
