package com.prospr.app.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

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
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final MemberLifestyleStatusRepository statusRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionCategorizationService categorizationService;
    private final MonthlySpendingSummaryService monthlySummaryService;
    private final LifestyleBaselineService baselineService;
    private final LifestyleInsightService insightService;
    private final LifestyleInsightRepository insightRepository;
    private final AnalyticsCacheService analyticsCacheService;

    public LifestyleAnalysisService(MemberLifestyleStatusRepository statusRepository,
                                     TransactionRepository transactionRepository,
                                     TransactionCategorizationService categorizationService,
                                     MonthlySpendingSummaryService monthlySummaryService,
                                     LifestyleBaselineService baselineService,
                                     LifestyleInsightService insightService,
                                     LifestyleInsightRepository insightRepository,
                                     AnalyticsCacheService analyticsCacheService) {
        this.statusRepository = statusRepository;
        this.transactionRepository = transactionRepository;
        this.categorizationService = categorizationService;
        this.monthlySummaryService = monthlySummaryService;
        this.baselineService = baselineService;
        this.insightService = insightService;
        this.insightRepository = insightRepository;
        this.analyticsCacheService = analyticsCacheService;
    }

    /** Runs the pipeline against whatever transaction history already exists for this member. */
    public void enable(Member member) {
        recomputeForMember(member);
    }

    public String getStatus(Member member) {
        return statusRepository.findByMemberId(member.getId())
                .map(MemberLifestyleStatus::getStatus)
                .orElse(LifestyleStatus.NOT_ENABLED);
    }

    public List<LifestyleInsight> getActiveInsightsForCurrentMonth(Member member) {
        YearMonth now = YearMonth.now();
        return insightRepository.findByMemberIdAndYearAndMonthAndStatus(
                member.getId(), now.getYear(), now.getMonthValue(), STATUS_ACTIVE);
    }

    /**
     * Hooked from {@link SetuSessionService} right after transactions are persisted for a session.
     * A no-op for members who never enabled lifestyle analysis (no status row yet), so ordinary
     * Setu users who never touched this feature never pay for it.
     */
    public void recomputeIfEnabled(Member member) {
        if (statusRepository.findByMemberId(member.getId()).isEmpty()) {
            return;
        }
        recomputeForMember(member);
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
            YearMonth currentMonth = YearMonth.now();
            LifestyleBaselineService.Result baselineResult = baselineService.recomputeForMember(member, currentMonth);

            if (baselineResult == LifestyleBaselineService.Result.INSUFFICIENT_HISTORY) {
                updateStatus(status, LifestyleStatus.INSUFFICIENT_HISTORY);
                return;
            }

            insightService.recomputeForMember(member, currentMonth, LocalDate.now());
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
