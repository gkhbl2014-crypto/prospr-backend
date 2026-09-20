package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.prospr.app.config.RecurringExpenseProperties;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.RecurringExpense;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.RecurringExpenseRepository;
import com.prospr.app.repository.TransactionRepository;

/**
 * Detects recurring payments (rent/EMI/insurance premium/subscription/SIP/utility/other) from a
 * member's transaction history: groups DEBIT transactions by normalized merchant + category, then
 * requires both amount regularity (every occurrence within a tolerance band of the median) and
 * interval regularity (gaps between occurrences consistently matching a monthly/quarterly/annual
 * cadence) before treating a pattern as recurring. A group that doesn't clear both bars simply
 * isn't stored - "not enough evidence yet" is silence, not a low-confidence guess.
 */
@Service
public class RecurringExpenseDetectionService {

    private static final Logger log = LoggerFactory.getLogger(RecurringExpenseDetectionService.class);
    private static final String DEBIT = "DEBIT";

    private static final String MONTHLY = "MONTHLY";
    private static final String QUARTERLY = "QUARTERLY";
    private static final String ANNUAL = "ANNUAL";

    private static final int NOMINAL_MONTHLY_DAYS = 30;
    private static final int NOMINAL_QUARTERLY_DAYS = 90;
    private static final int NOMINAL_ANNUAL_DAYS = 365;

    private static final String ACTIVE = "ACTIVE";
    private static final String LAPSED = "LAPSED";
    private static final String CONFIDENCE_HIGH = "HIGH";
    private static final String CONFIDENCE_MEDIUM = "MEDIUM";
    private static final String DETECTED = "DETECTED";
    private static final String UNCATEGORIZED = "UNCATEGORIZED";

    private final TransactionRepository transactionRepository;
    private final RecurringExpenseRepository recurringExpenseRepository;
    private final RecurringExpenseProperties properties;

    public RecurringExpenseDetectionService(TransactionRepository transactionRepository,
                                             RecurringExpenseRepository recurringExpenseRepository,
                                             RecurringExpenseProperties properties) {
        this.transactionRepository = transactionRepository;
        this.recurringExpenseRepository = recurringExpenseRepository;
        this.properties = properties;
    }

    private record MerchantCategoryKey(String merchantKey, String category) {
    }

    private record CadenceResult(String cadence, int nominalDays) {
    }

    public void recomputeForMember(Member member) {
        List<Transaction> candidates = transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())
                .stream()
                .filter(t -> DEBIT.equalsIgnoreCase(t.getType()))
                .filter(t -> t.getMerchantName() != null && !t.getMerchantName().isBlank())
                .filter(t -> t.getAmount() != null && t.getValueDate() != null)
                .toList();

        // A merchant can be recognized by name (normalization) without a recognized category
        // (categorization can legitimately fail or stay LOW-confidence/null) - recurring_expense.
        // category is NOT NULL, and a genuinely recurring payment (a phone bill, say) is exactly the
        // kind of thing worth surfacing even when the categorizer couldn't tag it, so substitute a
        // sentinel rather than crashing or silently dropping the pattern.
        Map<MerchantCategoryKey, List<Transaction>> grouped = candidates.stream()
                .collect(Collectors.groupingBy(t -> new MerchantCategoryKey(
                        t.getMerchantName().toLowerCase(Locale.ROOT),
                        Objects.requireNonNullElse(t.getEffectiveCategory(), UNCATEGORIZED))));

        int detected = 0;
        for (Map.Entry<MerchantCategoryKey, List<Transaction>> entry : grouped.entrySet()) {
            List<Transaction> group = new ArrayList<>(entry.getValue());
            if (group.size() < properties.getMinOccurrences()) {
                continue;
            }
            group.sort(Comparator.comparing(Transaction::getValueDate));

            if (!isAmountRegular(group, properties.getAmountTolerancePercent())) {
                continue;
            }
            CadenceResult cadence = detectCadence(group);
            if (cadence == null) {
                continue;
            }

            RecurringExpense recurring = upsert(member, entry.getKey(), group, cadence);
            recurringExpenseRepository.save(recurring);
            for (Transaction txn : group) {
                txn.setRecurringExpenseId(recurring.getId());
            }
            transactionRepository.saveAll(group);
            detected++;
        }
        log.info("Detected {} recurring expense pattern(s) for member '{}'", detected, member.getId());
    }

    private boolean isAmountRegular(List<Transaction> group, double tolerancePercent) {
        BigDecimal median = median(group);
        BigDecimal tolerance = median.multiply(BigDecimal.valueOf(tolerancePercent / 100.0));
        BigDecimal floor = BigDecimal.valueOf(properties.getAmountToleranceFloorInr());
        BigDecimal effectiveTolerance = tolerance.max(floor);
        for (Transaction txn : group) {
            BigDecimal diff = txn.getAmount().subtract(median).abs();
            if (diff.compareTo(effectiveTolerance) > 0) {
                return false;
            }
        }
        return true;
    }

    private BigDecimal median(List<Transaction> group) {
        List<BigDecimal> amounts = group.stream().map(Transaction::getAmount)
                .sorted().collect(Collectors.toList());
        int size = amounts.size();
        if (size % 2 == 1) {
            return amounts.get(size / 2);
        }
        return amounts.get(size / 2 - 1).add(amounts.get(size / 2))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    /** Every gap between consecutive occurrences must fall within tolerance of the same nominal cadence. */
    private CadenceResult detectCadence(List<Transaction> group) {
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < group.size(); i++) {
            gaps.add(ChronoUnit.DAYS.between(group.get(i - 1).getValueDate(), group.get(i).getValueDate()));
        }
        if (matchesCadence(gaps, NOMINAL_MONTHLY_DAYS, properties.getMonthlyIntervalToleranceDays())) {
            return new CadenceResult(MONTHLY, NOMINAL_MONTHLY_DAYS);
        }
        if (matchesCadence(gaps, NOMINAL_QUARTERLY_DAYS, properties.getQuarterlyIntervalToleranceDays())) {
            return new CadenceResult(QUARTERLY, NOMINAL_QUARTERLY_DAYS);
        }
        if (matchesCadence(gaps, NOMINAL_ANNUAL_DAYS, properties.getAnnualIntervalToleranceDays())) {
            return new CadenceResult(ANNUAL, NOMINAL_ANNUAL_DAYS);
        }
        return null;
    }

    private boolean matchesCadence(List<Long> gaps, int nominalDays, int toleranceDays) {
        return gaps.stream().allMatch(gap -> Math.abs(gap - nominalDays) <= toleranceDays);
    }

    private RecurringExpense upsert(Member member, MerchantCategoryKey key, List<Transaction> group, CadenceResult cadence) {
        Transaction latest = group.get(group.size() - 1);
        Transaction earliest = group.get(0);
        BigDecimal median = median(group);

        boolean highConfidence = MONTHLY.equals(cadence.cadence())
                && group.size() >= properties.getHighConfidenceMinOccurrences()
                && isAmountRegular(group, properties.getHighConfidenceAmountTolerancePercent());

        LocalDate today = LocalDate.now();
        boolean lapsed = ChronoUnit.DAYS.between(latest.getValueDate(), today)
                > cadence.nominalDays() * properties.getActiveLapseMultiplier();

        RecurringExpense recurring = recurringExpenseRepository
                .findByMemberIdAndMerchantKeyAndCategory(member.getId(), key.merchantKey(), key.category())
                .orElseGet(() -> RecurringExpense.builder().member(member)
                        .merchantKey(key.merchantKey())
                        .category(key.category())
                        .build());

        recurring.setClassification(classify(key.category(), latest.getNarration()));
        recurring.setTypicalAmount(median);
        recurring.setAmountTolerancePercent(BigDecimal.valueOf(properties.getAmountTolerancePercent()));
        recurring.setCadence(cadence.cadence());
        recurring.setOccurrencesCount(group.size());
        recurring.setFirstSeenDate(earliest.getValueDate());
        recurring.setLastSeenDate(latest.getValueDate());
        recurring.setNextExpectedDate(latest.getValueDate().plusDays(cadence.nominalDays()));
        recurring.setConfidence(highConfidence ? CONFIDENCE_HIGH : CONFIDENCE_MEDIUM);
        recurring.setStatus(lapsed ? LAPSED : ACTIVE);
        recurring.setDetectionStatus(DETECTED);
        recurring.setLastCalculatedAt(LocalDateTime.now());
        return recurring;
    }

    private String classify(String category, String narration) {
        if (category == null) {
            return "OTHER";
        }
        String upper = category.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "RENT" -> "RENT";
            case "EMI", "LOAN_PAYMENT" -> "EMI";
            case "INSURANCE" -> "INSURANCE_PREMIUM";
            case "SUBSCRIPTIONS" -> "SUBSCRIPTION";
            case "UTILITIES" -> "UTILITY";
            case "INVESTMENT" -> narration != null && narration.toLowerCase(Locale.ROOT).contains("sip")
                    ? "SIP" : "OTHER";
            default -> "OTHER";
        };
    }
}
