package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.prospr.app.dto.response.SafetyNetResponse;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.SafetyNetSettings;
import com.prospr.app.entity.SafetyNetSummary;
import com.prospr.app.repository.SafetyNetSettingsRepository;
import com.prospr.app.repository.SafetyNetSummaryRepository;

/**
 * Orchestrates the three Safety Net sub-calculations and caches the result in
 * {@code safety_net_summary}, mirroring the shape of {@link LifestyleAnalysisService} for this
 * codebase's other calculated-and-cached feature. Unlike Lifestyle, there's no opt-in gate: Safety
 * Net recomputes unconditionally whenever relevant source data changes, since it's meant to "just
 * work" once any transaction/insurance/allocation data exists for a member.
 */
@Service
public class SafetyNetService {

    private static final int DEFAULT_TARGET_MONTHS = 6;
    private static final int DEFAULT_LOOKBACK_MONTHS = 3;

    private final SafetyNetSettingsRepository settingsRepository;
    private final SafetyNetSummaryRepository summaryRepository;
    private final EssentialExpenseService essentialExpenseService;
    private final EmergencyFundService emergencyFundService;
    private final HealthCoverageService healthCoverageService;
    private final LifeCoverageService lifeCoverageService;

    public SafetyNetService(SafetyNetSettingsRepository settingsRepository,
                             SafetyNetSummaryRepository summaryRepository,
                             EssentialExpenseService essentialExpenseService,
                             EmergencyFundService emergencyFundService,
                             HealthCoverageService healthCoverageService,
                             LifeCoverageService lifeCoverageService) {
        this.settingsRepository = settingsRepository;
        this.summaryRepository = summaryRepository;
        this.essentialExpenseService = essentialExpenseService;
        this.emergencyFundService = emergencyFundService;
        this.healthCoverageService = healthCoverageService;
        this.lifeCoverageService = lifeCoverageService;
    }

    private record Computed(HealthCoverageService.Result health, LifeCoverageService.Result life,
                             EmergencyFundService.Result emergencyFund, LocalDateTime calculatedAt) {
    }

    /**
     * Recomputes and persists the cached summary for one member. Callers triggered by a direct user
     * write (insurance/allocation/settings CRUD) should let failures propagate normally. Callers
     * triggered by background sync (the Setu session hook) MUST wrap this in their own try/catch -
     * this method itself does not swallow exceptions, so a transient failure never silently leaves a
     * corrupted half-written cache row (the whole recompute either fully succeeds or the previous
     * cached row is left untouched).
     */
    public void recomputeForMember(Member member) {
        Computed computed = computeAll(member);
        SafetyNetSummary summary = summaryRepository.findByMemberId(member.getId())
                .orElseGet(() -> SafetyNetSummary.builder().member(member).build());
        applyTo(summary, computed);
        summaryRepository.save(summary);
    }

    /** Reads the cached summary if present; computes on-the-fly (without persisting) on cold start. */
    public SafetyNetResponse getForMember(Member member) {
        return summaryRepository.findByMemberId(member.getId())
                .map(this::toResponse)
                .orElseGet(() -> toResponse(computeAll(member)));
    }

    private Computed computeAll(Member member) {
        SafetyNetSettings settings = settingsRepository.findByMemberId(member.getId())
                .orElseGet(() -> transientDefaultSettings(member));

        EssentialExpenseService.Result essential = essentialExpenseService.computeAverage(
                member, settings.getEssentialExpenseLookbackMonths());
        EmergencyFundService.Result emergencyFund = emergencyFundService.compute(member, settings, essential);
        HealthCoverageService.Result health = healthCoverageService.compute(member);
        LifeCoverageService.Result life = lifeCoverageService.compute(member, settings,
                essential.averageMonthlyEssentialExpense());

        return new Computed(health, life, emergencyFund, LocalDateTime.now());
    }

    private SafetyNetSettings transientDefaultSettings(Member member) {
        return SafetyNetSettings.builder()
                .member(member)
                .emergencyFundTargetMonths(DEFAULT_TARGET_MONTHS)
                .essentialExpenseLookbackMonths(DEFAULT_LOOKBACK_MONTHS)
                .build();
    }

    private void applyTo(SafetyNetSummary summary, Computed computed) {
        HealthCoverageService.Result health = computed.health();
        summary.setIndividualHealthCoverage(health.individualCoverage());
        summary.setSharedHealthCoverage(health.sharedCoverage());
        summary.setTotalHealthCoverage(health.individualCoverage().add(health.sharedCoverage()));
        summary.setActiveHealthPolicyCount(health.activePolicyCount());
        summary.setHealthCoverageStatus(health.status());

        LifeCoverageService.Result life = computed.life();
        summary.setTotalLifeCoverage(life.totalCoverage());
        summary.setEstimatedLifeCoverageTarget(life.estimatedTarget());
        summary.setEstimatedLifeCoverageGap(life.estimatedGap());
        summary.setLifeCoverageStatus(life.status());

        EmergencyFundService.Result emergencyFund = computed.emergencyFund();
        summary.setEmergencyFundAmount(emergencyFund.currentAmount());
        summary.setAverageMonthlyEssentialExpense(emergencyFund.averageMonthlyEssentialExpense());
        summary.setEmergencyFundCoverageMonths(emergencyFund.coverageMonths());
        summary.setEmergencyFundTargetMonths(emergencyFund.targetMonths());
        summary.setEmergencyFundTargetAmount(emergencyFund.targetAmount());
        summary.setEmergencyFundGap(emergencyFund.gap());
        summary.setEmergencyFundStatus(emergencyFund.status());

        summary.setCalculatedAt(computed.calculatedAt());
    }

    private SafetyNetResponse toResponse(Computed computed) {
        SafetyNetResponse.Health health = SafetyNetResponse.Health.builder()
                .individualCoverage(computed.health().individualCoverage())
                .sharedCoverage(computed.health().sharedCoverage())
                .totalCoverage(computed.health().individualCoverage().add(computed.health().sharedCoverage()))
                .activePolicyCount(computed.health().activePolicyCount())
                .status(computed.health().status())
                .build();

        SafetyNetResponse.Life life = SafetyNetResponse.Life.builder()
                .totalCoverage(computed.life().totalCoverage())
                .estimatedTarget(computed.life().estimatedTarget())
                .estimatedGap(computed.life().estimatedGap())
                .status(computed.life().status())
                .build();

        SafetyNetResponse.EmergencyFund emergencyFund = SafetyNetResponse.EmergencyFund.builder()
                .currentAmount(computed.emergencyFund().currentAmount())
                .averageMonthlyEssentialExpense(computed.emergencyFund().averageMonthlyEssentialExpense())
                .coverageMonths(computed.emergencyFund().coverageMonths())
                .targetMonths(computed.emergencyFund().targetMonths())
                .targetAmount(computed.emergencyFund().targetAmount())
                .gap(computed.emergencyFund().gap())
                .status(computed.emergencyFund().status())
                .build();

        return SafetyNetResponse.builder()
                .health(health)
                .life(life)
                .emergencyFund(emergencyFund)
                .insights(buildInsights(health, life, emergencyFund))
                .calculatedAt(computed.calculatedAt())
                .build();
    }

    private SafetyNetResponse toResponse(SafetyNetSummary summary) {
        SafetyNetResponse.Health health = SafetyNetResponse.Health.builder()
                .individualCoverage(summary.getIndividualHealthCoverage())
                .sharedCoverage(summary.getSharedHealthCoverage())
                .totalCoverage(summary.getTotalHealthCoverage())
                .activePolicyCount(summary.getActiveHealthPolicyCount())
                .status(summary.getHealthCoverageStatus())
                .build();

        SafetyNetResponse.Life life = SafetyNetResponse.Life.builder()
                .totalCoverage(summary.getTotalLifeCoverage())
                .estimatedTarget(summary.getEstimatedLifeCoverageTarget())
                .estimatedGap(summary.getEstimatedLifeCoverageGap())
                .status(summary.getLifeCoverageStatus())
                .build();

        SafetyNetResponse.EmergencyFund emergencyFund = SafetyNetResponse.EmergencyFund.builder()
                .currentAmount(summary.getEmergencyFundAmount())
                .averageMonthlyEssentialExpense(summary.getAverageMonthlyEssentialExpense())
                .coverageMonths(summary.getEmergencyFundCoverageMonths())
                .targetMonths(summary.getEmergencyFundTargetMonths())
                .targetAmount(summary.getEmergencyFundTargetAmount())
                .gap(summary.getEmergencyFundGap())
                .status(summary.getEmergencyFundStatus())
                .build();

        return SafetyNetResponse.builder()
                .health(health)
                .life(life)
                .emergencyFund(emergencyFund)
                .insights(buildInsights(health, life, emergencyFund))
                .calculatedAt(summary.getCalculatedAt())
                .build();
    }

    /** Only ever derived from already-computed numbers - never queries source data again. */
    private List<SafetyNetResponse.Insight> buildInsights(SafetyNetResponse.Health health,
                                                            SafetyNetResponse.Life life,
                                                            SafetyNetResponse.EmergencyFund emergencyFund) {
        List<SafetyNetResponse.Insight> insights = new ArrayList<>();

        if (HealthCoverageStatus.NO_COVER.equals(health.getStatus())) {
            insights.add(insight("HEALTH_NO_COVER", "CRITICAL",
                    "You have no active health insurance coverage recorded."));
        }

        if (LifeCoverageStatus.GAP_DETECTED.equals(life.getStatus()) && life.getEstimatedGap() != null) {
            insights.add(insight("LIFE_GAP", "WARNING",
                    "Your life insurance coverage has an estimated protection gap of "
                            + formatInr(life.getEstimatedGap()) + ". This is an estimate, not financial advice."));
        } else if (LifeCoverageStatus.REVIEW_RECOMMENDED.equals(life.getStatus())) {
            insights.add(insight("LIFE_REVIEW_RECOMMENDED", "INFO",
                    "Add financial details to estimate your life protection gap."));
        }

        if (emergencyFund.getGap() != null && emergencyFund.getGap().compareTo(BigDecimal.ZERO) > 0) {
            insights.add(insight("EMERGENCY_FUND_GAP", "WARNING",
                    "You need approximately " + formatInr(emergencyFund.getGap())
                            + " more to reach your emergency fund target."));
        }

        if (emergencyFund.getCoverageMonths() != null) {
            insights.add(insight("EMERGENCY_FUND_COVERAGE", "INFO",
                    "Your emergency fund covers approximately " + emergencyFund.getCoverageMonths()
                            + " months of essential expenses."));
        } else if (EmergencyFundStatus.UNKNOWN.equals(emergencyFund.getStatus())) {
            insights.add(insight("EMERGENCY_FUND_UNKNOWN", "INFO",
                    "Add transaction history to estimate your essential monthly expenses."));
        }

        insights.sort(Comparator.comparingInt(i -> severityRank(i.getSeverity())));
        return insights;
    }

    private SafetyNetResponse.Insight insight(String type, String severity, String message) {
        return SafetyNetResponse.Insight.builder().type(type).severity(severity).message(message).build();
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        };
    }

    private String formatInr(BigDecimal amount) {
        NumberFormat format = NumberFormat.getIntegerInstance(new Locale("en", "IN"));
        return "₹" + format.format(amount.setScale(0, RoundingMode.HALF_UP));
    }
}
