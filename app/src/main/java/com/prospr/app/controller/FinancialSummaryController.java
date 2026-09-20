package com.prospr.app.controller;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.response.CategoryAmount;
import com.prospr.app.dto.response.MonthlySnapshotResponse;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberMonthlySnapshot;
import com.prospr.app.entity.MemberMonthlySummary;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.MemberMonthlySnapshotRepository;
import com.prospr.app.repository.MemberMonthlySummaryRepository;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.CategoryTaxonomy;
import com.prospr.app.service.FinancialAnalysisOrchestratorService;
import com.prospr.app.service.cache.AnalyticsCacheService;
import com.prospr.app.service.cache.CacheKeys;

/** Self-only: every endpoint resolves the member from the JWT, matching every other controller here. */
@RestController
@RequestMapping("/api/financial-summary")
public class FinancialSummaryController {

    private static final int DEFAULT_MONTHS = 6;
    private static final int TOP_CATEGORY_LIMIT = 5;

    private final MemberMonthlySnapshotRepository snapshotRepository;
    private final MemberMonthlySummaryRepository summaryRepository;
    private final MemberRepository memberRepository;
    private final FinancialAnalysisOrchestratorService financialAnalysisOrchestratorService;
    private final CategoryTaxonomy categoryTaxonomy;
    private final AnalyticsCacheService analyticsCacheService;

    public FinancialSummaryController(MemberMonthlySnapshotRepository snapshotRepository,
                                       MemberMonthlySummaryRepository summaryRepository,
                                       MemberRepository memberRepository,
                                       FinancialAnalysisOrchestratorService financialAnalysisOrchestratorService,
                                       CategoryTaxonomy categoryTaxonomy,
                                       AnalyticsCacheService analyticsCacheService) {
        this.snapshotRepository = snapshotRepository;
        this.summaryRepository = summaryRepository;
        this.memberRepository = memberRepository;
        this.financialAnalysisOrchestratorService = financialAnalysisOrchestratorService;
        this.categoryTaxonomy = categoryTaxonomy;
        this.analyticsCacheService = analyticsCacheService;
    }

    /**
     * Cache-aside, keyed on the member only (not on {@code months}) - the cache holds the member's
     * whole snapshot history as one entry, and the requested window is sliced from it after the
     * cache read. This avoids a combinatorial key per distinct {@code months} value and the wildcard
     * eviction that would otherwise require.
     */
    @GetMapping("/monthly")
    public ResponseEntity<List<MonthlySnapshotResponse>> monthly(
            @RequestParam(name = "months", required = false) Integer months, Authentication authentication) {
        Member member = resolveMember(authentication);
        int limit = (months == null || months < 1) ? DEFAULT_MONTHS : months;

        List<MonthlySnapshotResponse> ascending = loadAscendingHistory(member);
        int fromIndex = Math.max(0, ascending.size() - limit);
        return ResponseEntity.ok(ascending.subList(fromIndex, ascending.size()));
    }

    private List<MonthlySnapshotResponse> loadAscendingHistory(Member member) {
        Optional<List<MonthlySnapshotResponse>> cached = analyticsCacheService.getSpendingSummary(member.getId());
        if (cached.isPresent()) {
            return cached.get();
        }
        return analyticsCacheService.withStampedeGuard(CacheKeys.spendingSummary(member.getId()), () -> {
            Optional<List<MonthlySnapshotResponse>> recheck = analyticsCacheService.getSpendingSummary(member.getId());
            if (recheck.isPresent()) {
                return recheck.get();
            }
            List<MonthlySnapshotResponse> computed = computeAscendingHistory(member.getId());
            analyticsCacheService.putSpendingSummary(member.getId(), computed);
            return computed;
        });
    }

    private List<MonthlySnapshotResponse> computeAscendingHistory(UUID memberId) {
        List<MemberMonthlySnapshot> descending = snapshotRepository.findByMemberIdOrderByYearDescMonthDesc(memberId);
        List<MemberMonthlySnapshot> ascending = new ArrayList<>(descending);
        ascending.sort(Comparator.comparing(MemberMonthlySnapshot::getYear)
                .thenComparing(MemberMonthlySnapshot::getMonth));

        List<MonthlySnapshotResponse> responses = new ArrayList<>();
        for (int i = 0; i < ascending.size(); i++) {
            MemberMonthlySnapshot current = ascending.get(i);
            MemberMonthlySnapshot previous = i > 0 ? ascending.get(i - 1) : null;
            responses.add(toResponse(memberId, current, previous));
        }
        return responses;
    }

    @PostMapping("/recompute")
    public ResponseEntity<Void> recompute(Authentication authentication) {
        Member member = resolveMember(authentication);
        financialAnalysisOrchestratorService.recomputeForMember(member);
        return ResponseEntity.noContent().build();
    }

    private MonthlySnapshotResponse toResponse(UUID memberId, MemberMonthlySnapshot snapshot,
                                                MemberMonthlySnapshot previous) {
        String monthLabel = Month.of(snapshot.getMonth()).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                + " " + snapshot.getYear();

        List<MemberMonthlySummary> categorySummaries =
                summaryRepository.findByMemberIdAndYearAndMonth(memberId, snapshot.getYear(), snapshot.getMonth());
        List<CategoryAmount> topCategories = categorySummaries.stream()
                .sorted(Comparator.comparing(MemberMonthlySummary::getTotalAmount).reversed())
                .limit(TOP_CATEGORY_LIMIT)
                .map(s -> CategoryAmount.builder()
                        .category(s.getCategory())
                        .topLevelCategory(categoryTaxonomy.topLevelName(s.getCategory()))
                        .amount(s.getTotalAmount())
                        .build())
                .toList();

        return MonthlySnapshotResponse.builder()
                .year(snapshot.getYear())
                .month(snapshot.getMonth())
                .monthLabel(monthLabel)
                .totalIncome(snapshot.getTotalIncome())
                .totalEssential(snapshot.getTotalEssential())
                .totalDiscretionary(snapshot.getTotalDiscretionary())
                .totalInvestment(snapshot.getTotalInvestment())
                .totalDebtRepayment(snapshot.getTotalDebtRepayment())
                .totalInsurance(snapshot.getTotalInsurance())
                .totalInternalTransfer(snapshot.getTotalInternalTransfer())
                .totalCashWithdrawal(snapshot.getTotalCashWithdrawal())
                .totalUnknown(snapshot.getTotalUnknown())
                .totalExpenses(snapshot.getTotalExpenses())
                .savings(snapshot.getSavings())
                .savingsRate(snapshot.getSavingsRate())
                .transactionCount(snapshot.getTransactionCount())
                .averageTransactionValue(snapshot.getAverageTransactionValue())
                .topCategories(topCategories)
                .hasData(Boolean.TRUE.equals(snapshot.getHasData()))
                .incomeChangeFromPreviousMonth(previous == null ? null
                        : snapshot.getTotalIncome().subtract(previous.getTotalIncome()))
                .expensesChangeFromPreviousMonth(previous == null ? null
                        : snapshot.getTotalExpenses().subtract(previous.getTotalExpenses()))
                .savingsRateChangeFromPreviousMonth(previous == null || snapshot.getSavingsRate() == null
                        || previous.getSavingsRate() == null ? null
                        : snapshot.getSavingsRate().subtract(previous.getSavingsRate()))
                .calculatedAt(snapshot.getCalculatedAt())
                .build();
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
