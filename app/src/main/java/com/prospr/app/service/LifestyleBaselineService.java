package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.prospr.app.config.LifestyleProperties;
import com.prospr.app.entity.LifestyleBaseline;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberMonthlySummary;
import com.prospr.app.repository.LifestyleBaselineRepository;
import com.prospr.app.repository.MemberMonthlySummaryRepository;
import com.prospr.app.repository.TransactionRepository;

/**
 * Builds each member's "normal spend" baseline per lifestyle category from the previous 3
 * COMPLETED calendar months (never the current, possibly-partial, month).
 */
@Service
public class LifestyleBaselineService {

    private static final Logger log = LoggerFactory.getLogger(LifestyleBaselineService.class);

    public enum Result {
        COMPUTED, INSUFFICIENT_HISTORY
    }

    private final MemberMonthlySummaryRepository summaryRepository;
    private final LifestyleBaselineRepository baselineRepository;
    private final TransactionRepository transactionRepository;
    private final LifestyleCategoryCatalog catalog;
    private final LifestyleProperties properties;

    public LifestyleBaselineService(MemberMonthlySummaryRepository summaryRepository,
                                     LifestyleBaselineRepository baselineRepository,
                                     TransactionRepository transactionRepository,
                                     LifestyleCategoryCatalog catalog,
                                     LifestyleProperties properties) {
        this.summaryRepository = summaryRepository;
        this.baselineRepository = baselineRepository;
        this.transactionRepository = transactionRepository;
        this.catalog = catalog;
        this.properties = properties;
    }

    /**
     * @param currentMonth the calendar month analysis is being run for; baseline months are the
     *                     three calendar months strictly before it (e.g. current=August ->
     *                     May, June, July - never "June 15 to August 15").
     */
    public Result recomputeForMember(Member member, YearMonth currentMonth) {
        List<YearMonth> baselineMonths = List.of(
                currentMonth.minusMonths(3), currentMonth.minusMonths(2), currentMonth.minusMonths(1));
        LocalDate earliestBaselineMonthStart = baselineMonths.get(0).atDay(1);

        // Sufficiency is about whether the member's fetched transaction history reaches back to the
        // start of the earliest baseline month at all - not about whether every category has spend
        // in every month. This is a single member-level check (not per-category) because it reflects
        // how much history Setu actually gave us, which is the same for every category.
        boolean hasHistoryCoverage = transactionRepository
                .existsByMemberIdAndValueDateLessThanEqual(member.getId(), earliestBaselineMonthStart);
        if (!hasHistoryCoverage) {
            log.info("Member '{}' has insufficient transaction history for a lifestyle baseline (need data back to {})",
                    member.getId(), earliestBaselineMonthStart);
            return Result.INSUFFICIENT_HISTORY;
        }

        for (String category : catalog.lifestyleCategories()) {
            List<BigDecimal> monthlyTotals = new ArrayList<>();
            for (YearMonth month : baselineMonths) {
                // Coverage is already confirmed above, so the absence of a summary row for this
                // month/category is treated as genuine zero spend that month, not missing data.
                BigDecimal amount = summaryRepository
                        .findByMemberIdAndYearAndMonthAndCategory(member.getId(), month.getYear(), month.getMonthValue(), category)
                        .map(MemberMonthlySummary::getTotalAmount)
                        .orElse(BigDecimal.ZERO);
                monthlyTotals.add(amount);
            }

            BigDecimal sum = monthlyTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal average = sum.divide(BigDecimal.valueOf(monthlyTotals.size()), 2, RoundingMode.HALF_UP);
            BigDecimal minimum = monthlyTotals.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
            BigDecimal maximum = monthlyTotals.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);

            LifestyleBaseline baseline = baselineRepository.findByMemberIdAndCategory(member.getId(), category)
                    .orElseGet(() -> LifestyleBaseline.builder().member(member).category(category).build());
            baseline.setBaselineMonths(properties.getBaselineMonths());
            baseline.setBaselineAmount(average);
            baseline.setMinimumAmount(minimum);
            baseline.setMaximumAmount(maximum);
            baseline.setLastCalculatedAt(LocalDateTime.now());
            baselineRepository.save(baseline);
        }

        log.info("Recomputed lifestyle baselines for member '{}' from months {}", member.getId(), baselineMonths);
        return Result.COMPUTED;
    }
}
