package com.prospr.app.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.config.LifestyleProperties;
import com.prospr.app.dto.response.CategoryAmount;
import com.prospr.app.dto.response.LifestyleCreepSummaryResponse;
import com.prospr.app.dto.response.LifestyleStatusResponse;
import com.prospr.app.entity.LifestyleInsight;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.AnalysisConfidenceService;
import com.prospr.app.service.LifestyleAnalysisService;
import com.prospr.app.service.LifestyleCategoryCatalog;
import com.prospr.app.service.LifestyleStatus;
import com.prospr.app.service.cache.AnalyticsCacheService;
import com.prospr.app.service.cache.CacheKeys;

@RestController
@RequestMapping("/api/lifestyle")
public class LifestyleController {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final LifestyleAnalysisService lifestyleAnalysisService;
    private final LifestyleCategoryCatalog categoryCatalog;
    private final MemberRepository memberRepository;
    private final LifestyleProperties lifestyleProperties;
    private final AnalyticsCacheService analyticsCacheService;
    private final AnalysisConfidenceService analysisConfidenceService;

    public LifestyleController(LifestyleAnalysisService lifestyleAnalysisService,
                                LifestyleCategoryCatalog categoryCatalog,
                                MemberRepository memberRepository,
                                LifestyleProperties lifestyleProperties,
                                AnalyticsCacheService analyticsCacheService,
                                AnalysisConfidenceService analysisConfidenceService) {
        this.lifestyleAnalysisService = lifestyleAnalysisService;
        this.categoryCatalog = categoryCatalog;
        this.memberRepository = memberRepository;
        this.lifestyleProperties = lifestyleProperties;
        this.analyticsCacheService = analyticsCacheService;
        this.analysisConfidenceService = analysisConfidenceService;
    }

    /** Runs lifestyle analysis against the member's already-stored transaction history. */
    @PostMapping("/enable")
    public ResponseEntity<LifestyleStatusResponse> enable(Authentication authentication) {
        Member member = resolveMember(authentication);
        lifestyleAnalysisService.enable(member);
        String status = lifestyleAnalysisService.getStatus(member);
        return ResponseEntity.ok(LifestyleStatusResponse.builder()
                .status(status)
                .message(describeStatus(status))
                .build());
    }

    @GetMapping("/status")
    public ResponseEntity<LifestyleStatusResponse> status(Authentication authentication) {
        Member member = resolveMember(authentication);
        String status = lifestyleAnalysisService.getStatus(member);
        return ResponseEntity.ok(LifestyleStatusResponse.builder()
                .status(status)
                .message(describeStatus(status))
                .build());
    }

    /**
     * Cache-aside: Redis first, falling back to the existing Postgres-backed lookup + mapping on a
     * miss (or if Redis is unavailable). A cached response's {@code periodLabel}/{@code monthToDate}
     * fields are computed relative to "now" at write time, so they can be up to the cache TTL stale
     * right at a month boundary - an acceptable trade for a 30-minute TTL, not worth invalidating on
     * a timer for.
     */
    @GetMapping("/insights")
    public ResponseEntity<LifestyleCreepSummaryResponse> insights(Authentication authentication) {
        Member member = resolveMember(authentication);

        Optional<LifestyleCreepSummaryResponse> cached = analyticsCacheService.getLifestyleCreep(member.getId());
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.orElse(null));
        }
        LifestyleCreepSummaryResponse summary = analyticsCacheService.withStampedeGuard(
                CacheKeys.lifestyleCreep(member.getId()), () -> {
                    Optional<LifestyleCreepSummaryResponse> recheck = analyticsCacheService.getLifestyleCreep(member.getId());
                    if (recheck.isPresent()) {
                        return recheck.orElse(null);
                    }
                    LifestyleCreepSummaryResponse computed = lifestyleAnalysisService.getCurrentMonthSummary(member)
                            .map(this::toResponse)
                            .orElse(null);
                    analyticsCacheService.putLifestyleCreep(member.getId(), computed);
                    return computed;
                });
        return ResponseEntity.ok(summary);
    }

    /**
     * Manual trigger for testing: recomputes the CALLING member's own lifestyle analysis on demand.
     * Deliberately scoped to "self" only (resolved from the JWT, no memberId parameter) so it can't
     * be used to force recomputation for anyone else - safe to leave behind standard auth rather
     * than needing a separate admin role that doesn't exist in this app yet.
     */
    @PostMapping("/analyze")
    public ResponseEntity<LifestyleStatusResponse> analyze(Authentication authentication) {
        Member member = resolveMember(authentication);
        lifestyleAnalysisService.recomputeForMember(member);
        String status = lifestyleAnalysisService.getStatus(member);
        return ResponseEntity.ok(LifestyleStatusResponse.builder()
                .status(status)
                .message(describeStatus(status))
                .build());
    }

    private LifestyleCreepSummaryResponse toResponse(LifestyleInsight insight) {
        YearMonth insightMonth = YearMonth.of(insight.getYear(), insight.getMonth());
        YearMonth currentMonth = YearMonth.now();
        LocalDate today = LocalDate.now();
        boolean monthToDate = insightMonth.equals(currentMonth) && today.getDayOfMonth() < currentMonth.lengthOfMonth();

        String periodLabel = monthToDate
                ? "Month to date (1–" + today.format(MONTH_FORMAT) + ")"
                : insightMonth.atDay(1).format(MONTH_YEAR_FORMAT);

        AnalysisConfidenceService.Confidence confidence = analysisConfidenceService
                .forLifestyle(insight.getMember().getId(), insightMonth);

        return LifestyleCreepSummaryResponse.builder()
                .confidenceLevel(confidence.level())
                .confidenceReason(confidence.reason())
                .unexplainedAmount(confidence.unexplainedAmount())
                .explainedPercent(confidence.explainedPercent())
                .state(insight.getSeverity())
                .periodLabel(periodLabel)
                .monthToDate(monthToDate)
                .currentAmount(insight.getCurrentAmount())
                .baselineAmount(insight.getBaselineAmount())
                .differenceAmount(insight.getDifferenceAmount())
                .increasePercentage(insight.getIncreasePercentage())
                .consecutiveElevatedMonths(insight.getConsecutiveElevatedMonths() == null ? 0 : insight.getConsecutiveElevatedMonths())
                .message(insight.getMessage())
                .topIncreases(parseTopIncreases(insight.getTopCategoryIncreases()))
                .newRecurringExpenseLabel(insight.getNewRecurringExpenseLabel())
                .newRecurringExpenseAmount(insight.getNewRecurringExpenseAmount())
                .incomeGrowthPercent(insight.getIncomeGrowthPercent())
                .build();
    }

    /** Parses the entity's simple "CODE:amount,CODE:amount" serialization back into display-ready
     *  category amounts - no child table for what's at most 2 values. */
    private List<CategoryAmount> parseTopIncreases(String serialized) {
        List<CategoryAmount> result = new ArrayList<>();
        if (serialized == null || serialized.isBlank()) {
            return result;
        }
        for (String entry : serialized.split(",")) {
            String[] parts = entry.split(":");
            if (parts.length != 2) {
                continue;
            }
            result.add(CategoryAmount.builder()
                    .category(parts[0])
                    .topLevelCategory(categoryCatalog.label(parts[0]))
                    .amount(new java.math.BigDecimal(parts[1]))
                    .build());
        }
        return result;
    }

    private String describeStatus(String status) {
        return switch (status) {
            case LifestyleStatus.NOT_ENABLED -> "Lifestyle analysis hasn't been enabled yet.";
            case LifestyleStatus.CONSENT_PENDING -> "Waiting for account consent to be approved.";
            case LifestyleStatus.FETCHING_HISTORY -> "Loading your stored transaction history.";
            case LifestyleStatus.CATEGORIZING -> "Categorizing your spending.";
            case LifestyleStatus.BUILDING_BASELINE ->
                    "Building your " + lifestyleProperties.getBaselineMonths() + "-month spending baseline.";
            case LifestyleStatus.READY -> "Your lifestyle insights are ready.";
            case LifestyleStatus.INSUFFICIENT_HISTORY ->
                    "We need at least " + lifestyleProperties.getBaselineMonths()
                            + " completed months of transaction history to build a reliable baseline.";
            case LifestyleStatus.ERROR -> "Something went wrong while analyzing your spending.";
            default -> "";
        };
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
