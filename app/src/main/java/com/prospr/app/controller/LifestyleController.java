package com.prospr.app.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.config.LifestyleProperties;
import com.prospr.app.dto.response.LifestyleInsightResponse;
import com.prospr.app.dto.response.LifestyleStatusResponse;
import com.prospr.app.entity.LifestyleInsight;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.LifestyleAnalysisService;
import com.prospr.app.service.LifestyleCategoryCatalog;
import com.prospr.app.service.LifestyleStatus;

@RestController
@RequestMapping("/api/lifestyle")
public class LifestyleController {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final LifestyleAnalysisService lifestyleAnalysisService;
    private final LifestyleCategoryCatalog categoryCatalog;
    private final MemberRepository memberRepository;
    private final LifestyleProperties lifestyleProperties;

    public LifestyleController(LifestyleAnalysisService lifestyleAnalysisService,
                                LifestyleCategoryCatalog categoryCatalog,
                                MemberRepository memberRepository,
                                LifestyleProperties lifestyleProperties) {
        this.lifestyleAnalysisService = lifestyleAnalysisService;
        this.categoryCatalog = categoryCatalog;
        this.memberRepository = memberRepository;
        this.lifestyleProperties = lifestyleProperties;
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

    @GetMapping("/insights")
    public ResponseEntity<List<LifestyleInsightResponse>> insights(Authentication authentication) {
        Member member = resolveMember(authentication);
        List<LifestyleInsightResponse> insights = lifestyleAnalysisService.getActiveInsightsForCurrentMonth(member)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(insights);
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

    private LifestyleInsightResponse toResponse(LifestyleInsight insight) {
        YearMonth insightMonth = YearMonth.of(insight.getYear(), insight.getMonth());
        YearMonth currentMonth = YearMonth.now();
        LocalDate today = LocalDate.now();
        boolean monthToDate = insightMonth.equals(currentMonth) && today.getDayOfMonth() < currentMonth.lengthOfMonth();

        String periodLabel = monthToDate
                ? "Month to date (1–" + today.format(MONTH_FORMAT) + ")"
                : insightMonth.atDay(1).format(MONTH_YEAR_FORMAT);

        return LifestyleInsightResponse.builder()
                .category(insight.getCategory())
                .categoryLabel(categoryCatalog.label(insight.getCategory()))
                .baselineAmount(insight.getBaselineAmount())
                .currentAmount(insight.getCurrentAmount())
                .differenceAmount(insight.getDifferenceAmount())
                .increasePercentage(insight.getIncreasePercentage())
                .severity(insight.getSeverity())
                .message(insight.getMessage())
                .monthToDate(monthToDate)
                .periodLabel(periodLabel)
                .build();
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
