package com.prospr.app.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.prospr.app.config.ClassificationProperties;
import com.prospr.app.entity.Transaction;

/**
 * Computes lightweight pattern evidence for one counterparty's transactions - amount consistency and
 * a rough cadence guess - surfaced to the user as context when asking "how do you use payments to X"
 * (e.g. "3 payments of ~₹15,000, monthly"). Evidence only, never a verdict: nothing here assigns a
 * classification by itself - "Rahul, ₹15,000 x3, monthly" and "Rahul, ₹300/₹500/₹1,200, irregular" are
 * materially different situations, but both still just inform the question asked, never answer it.
 */
@Service
public class PayeePatternService {

    public static final String CADENCE_MONTHLY = "MONTHLY";
    public static final String CADENCE_IRREGULAR = "IRREGULAR";

    private static final int MONTHLY_MIN_DAYS = 25;
    private static final int MONTHLY_MAX_DAYS = 35;

    public record Evidence(int occurrences, BigDecimal typicalAmount, boolean amountConsistent, String cadenceGuess) {

        static Evidence insufficient(int occurrences) {
            return new Evidence(occurrences, null, false, null);
        }
    }

    private final ClassificationProperties properties;

    public PayeePatternService(ClassificationProperties properties) {
        this.properties = properties;
    }

    /** transactions must already be filtered to one member and one counterparty. */
    public Evidence evaluate(List<Transaction> transactions) {
        List<Transaction> usable = transactions.stream()
                .filter(txn -> txn.getAmount() != null && resolveDate(txn) != null)
                .sorted(Comparator.comparing(PayeePatternService::resolveDate))
                .toList();

        if (usable.size() < properties.getPatternMinOccurrences()) {
            return Evidence.insufficient(usable.size());
        }

        List<BigDecimal> amounts = usable.stream()
                .map(txn -> txn.getAmount().abs())
                .sorted()
                .toList();
        BigDecimal median = amounts.get(amounts.size() / 2);
        BigDecimal tolerance = median.multiply(
                BigDecimal.valueOf(properties.getPatternAmountTolerancePercent() / 100));
        boolean amountConsistent = amounts.stream()
                .allMatch(amount -> amount.subtract(median).abs().compareTo(tolerance) <= 0);

        return new Evidence(usable.size(), median, amountConsistent, guessCadence(usable));
    }

    private String guessCadence(List<Transaction> chronological) {
        List<Long> gapsInDays = new ArrayList<>();
        for (int i = 1; i < chronological.size(); i++) {
            gapsInDays.add(ChronoUnit.DAYS.between(resolveDate(chronological.get(i - 1)), resolveDate(chronological.get(i))));
        }
        boolean allMonthly = gapsInDays.stream().allMatch(gap -> gap >= MONTHLY_MIN_DAYS && gap <= MONTHLY_MAX_DAYS);
        return allMonthly ? CADENCE_MONTHLY : CADENCE_IRREGULAR;
    }

    private static LocalDate resolveDate(Transaction txn) {
        if (txn.getValueDate() != null) {
            return txn.getValueDate();
        }
        return txn.getTransactionTimestamp() != null ? txn.getTransactionTimestamp().toLocalDate() : null;
    }
}
