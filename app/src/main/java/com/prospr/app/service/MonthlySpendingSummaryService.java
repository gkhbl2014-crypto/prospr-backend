package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberMonthlySummary;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.MemberMonthlySummaryRepository;
import com.prospr.app.repository.TransactionRepository;

/**
 * (Re)builds member_monthly_summary from categorized debit transactions. Only DEBIT transactions
 * are ever summed here - credits (salary, refunds, dividends) never had a category assigned by
 * {@link TransactionCategorizationService} in the first place, so this query already excludes them
 * by construction rather than by amount sign.
 */
@Service
public class MonthlySpendingSummaryService {

    private static final Logger log = LoggerFactory.getLogger(MonthlySpendingSummaryService.class);
    private static final String DEBIT = "DEBIT";

    private final TransactionRepository transactionRepository;
    private final MemberMonthlySummaryRepository summaryRepository;

    public MonthlySpendingSummaryService(TransactionRepository transactionRepository,
                                          MemberMonthlySummaryRepository summaryRepository) {
        this.transactionRepository = transactionRepository;
        this.summaryRepository = summaryRepository;
    }

    private record MonthCategoryKey(int year, int month, String category) {
    }

    public void recomputeForMember(Member member) {
        List<Transaction> transactions = transactionRepository
                .findByMemberIdAndCategoryIsNotNullAndTypeIgnoreCase(member.getId(), DEBIT);

        Map<MonthCategoryKey, List<Transaction>> grouped = transactions.stream()
                .filter(txn -> txn.getAmount() != null && resolveDate(txn) != null)
                .collect(Collectors.groupingBy(txn -> {
                    LocalDate date = resolveDate(txn);
                    return new MonthCategoryKey(date.getYear(), date.getMonthValue(), txn.getCategory());
                }));

        for (Map.Entry<MonthCategoryKey, List<Transaction>> entry : grouped.entrySet()) {
            MonthCategoryKey key = entry.getKey();
            List<Transaction> group = entry.getValue();

            BigDecimal total = group.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            int count = group.size();
            BigDecimal average = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

            MemberMonthlySummary summary = summaryRepository
                    .findByMemberIdAndYearAndMonthAndCategory(member.getId(), key.year(), key.month(), key.category())
                    .orElseGet(() -> MemberMonthlySummary.builder()
                            .member(member)
                            .year(key.year())
                            .month(key.month())
                            .category(key.category())
                            .build());

            summary.setTotalAmount(total);
            summary.setTransactionCount(count);
            summary.setAverageTransaction(average);
            summaryRepository.save(summary);
        }

        log.info("Recomputed {} monthly summary row(s) for member '{}'", grouped.size(), member.getId());
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
