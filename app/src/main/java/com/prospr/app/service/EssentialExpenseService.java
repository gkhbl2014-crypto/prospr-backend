package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.prospr.app.entity.Member;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.TransactionRepository;

/**
 * Shared helper for anything needing a member's "normal" monthly essential spend - currently
 * {@code EmergencyFundService} and {@code LifeCoverageService}. Deliberately independent of the
 * separate Lifestyle Analysis feature (which only populates {@code member_monthly_summary} for
 * members who opted in): Safety Net must work whether or not Lifestyle was ever enabled, so this
 * reads {@link Transaction} directly and reuses {@link TransactionCategorizationService} itself.
 */
@Service
public class EssentialExpenseService {

    private static final String DEBIT = "DEBIT";

    private final TransactionRepository transactionRepository;
    private final TransactionCategorizationService categorizationService;
    private final EssentialCategoryCatalog essentialCategoryCatalog;

    public EssentialExpenseService(TransactionRepository transactionRepository,
                                    TransactionCategorizationService categorizationService,
                                    EssentialCategoryCatalog essentialCategoryCatalog) {
        this.transactionRepository = transactionRepository;
        this.categorizationService = categorizationService;
        this.essentialCategoryCatalog = essentialCategoryCatalog;
    }

    /**
     * @param averageMonthlyEssentialExpense null if none of the last {@code lookbackMonths}
     *                                       completed months have any transaction history at all
     *                                       (distinct from having history with zero essential spend)
     * @param monthsWithData                 how many of the lookback months actually had transaction
     *                                       history - i.e. the denominator behind the average
     */
    public record Result(BigDecimal averageMonthlyEssentialExpense, int monthsWithData, int monthsConfigured) {
    }

    public Result computeAverage(Member member, int lookbackMonths) {
        return computeAverage(member, lookbackMonths, YearMonth.now());
    }

    /** @param asOfMonth the current month - completed months are the {@code lookbackMonths} before it. */
    public Result computeAverage(Member member, int lookbackMonths, YearMonth asOfMonth) {
        ensureCategorized(member);

        List<YearMonth> lookbackWindow = new ArrayList<>();
        for (int i = 1; i <= lookbackMonths; i++) {
            lookbackWindow.add(asOfMonth.minusMonths(i));
        }

        Map<YearMonth, Boolean> monthHasData = new HashMap<>();
        Map<YearMonth, BigDecimal> essentialTotalByMonth = new HashMap<>();
        for (YearMonth month : lookbackWindow) {
            monthHasData.put(month, false);
            essentialTotalByMonth.put(month, BigDecimal.ZERO);
        }

        List<Transaction> transactions = transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId());
        for (Transaction txn : transactions) {
            LocalDate date = resolveDate(txn);
            if (date == null) {
                continue;
            }
            YearMonth month = YearMonth.from(date);
            if (!monthHasData.containsKey(month)) {
                continue; // outside the lookback window
            }
            monthHasData.put(month, true);
            if (DEBIT.equalsIgnoreCase(txn.getType()) && essentialCategoryCatalog.isEssential(txn.getCategory())
                    && txn.getAmount() != null) {
                essentialTotalByMonth.merge(month, txn.getAmount(), BigDecimal::add);
            }
        }

        List<BigDecimal> monthsWithDataTotals = lookbackWindow.stream()
                .filter(monthHasData::get)
                .map(essentialTotalByMonth::get)
                .toList();

        BigDecimal average = monthsWithDataTotals.isEmpty()
                ? null
                : monthsWithDataTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(monthsWithDataTotals.size()), 2, RoundingMode.HALF_UP);

        return new Result(average, monthsWithDataTotals.size(), lookbackMonths);
    }

    private void ensureCategorized(Member member) {
        List<Transaction> uncategorized = transactionRepository.findByMemberIdAndCategoryIsNull(member.getId());
        if (!uncategorized.isEmpty()) {
            categorizationService.categorize(uncategorized);
            transactionRepository.saveAll(uncategorized);
        }
    }

    private LocalDate resolveDate(Transaction txn) {
        if (txn.getValueDate() != null) {
            return txn.getValueDate();
        }
        if (txn.getTransactionTimestamp() != null) {
            return txn.getTransactionTimestamp().toLocalDate();
        }
        return null;
    }
}
