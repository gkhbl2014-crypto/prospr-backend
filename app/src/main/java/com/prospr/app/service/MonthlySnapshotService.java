package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.prospr.app.config.FinancialAnalysisProperties;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberMonthlySnapshot;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.MemberMonthlySnapshotRepository;
import com.prospr.app.repository.TransactionRepository;

/**
 * Builds the whole-month income/expense/savings picture ({@link MemberMonthlySnapshot}) for the
 * trailing {@link FinancialAnalysisProperties#getSnapshotHistoryMonths()} months, driven entirely by
 * {@code transactionType} ({@link TransactionClassificationService}) rather than raw category - so a
 * SETU transaction and a MANUAL import feed the exact same buckets. Distinct from
 * {@link MonthlySpendingSummaryService}, which only tracks per-category DEBIT sums for Lifestyle's
 * rolling baseline and has no concept of income or savings at all.
 */
@Service
public class MonthlySnapshotService {

    private static final Logger log = LoggerFactory.getLogger(MonthlySnapshotService.class);

    private final TransactionRepository transactionRepository;
    private final MemberMonthlySnapshotRepository snapshotRepository;
    private final FinancialAnalysisProperties properties;

    public MonthlySnapshotService(TransactionRepository transactionRepository,
                                   MemberMonthlySnapshotRepository snapshotRepository,
                                   FinancialAnalysisProperties properties) {
        this.transactionRepository = transactionRepository;
        this.snapshotRepository = snapshotRepository;
        this.properties = properties;
    }

    private static final class MonthTotals {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal essential = BigDecimal.ZERO;
        BigDecimal discretionary = BigDecimal.ZERO;
        BigDecimal investment = BigDecimal.ZERO;
        BigDecimal debtRepayment = BigDecimal.ZERO;
        BigDecimal insurance = BigDecimal.ZERO;
        BigDecimal internalTransfer = BigDecimal.ZERO;
        BigDecimal cashWithdrawal = BigDecimal.ZERO;
        BigDecimal unknown = BigDecimal.ZERO;
        BigDecimal sumAbsAmount = BigDecimal.ZERO;
        int transactionCount = 0;
    }

    public void recomputeForMember(Member member) {
        List<Transaction> all = transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId());
        YearMonth current = YearMonth.now();
        int months = properties.getSnapshotHistoryMonths();
        for (int i = 0; i < months; i++) {
            recomputeMonth(member, current.minusMonths(i), all);
        }
        log.info("Recomputed {} month(s) of financial snapshot for member '{}'", months, member.getId());
    }

    private void recomputeMonth(Member member, YearMonth yearMonth, List<Transaction> all) {
        MonthTotals totals = new MonthTotals();
        for (Transaction txn : all) {
            LocalDate date = resolveDate(txn);
            if (date == null || !YearMonth.from(date).equals(yearMonth) || txn.getAmount() == null) {
                continue;
            }
            totals.transactionCount++;
            totals.sumAbsAmount = totals.sumAbsAmount.add(txn.getAmount().abs());
            addToBucket(totals, txn);
        }

        BigDecimal totalExpenses = totals.essential.add(totals.discretionary).add(totals.debtRepayment)
                .add(totals.insurance).add(totals.cashWithdrawal);
        boolean hasData = totals.transactionCount > 0;

        MemberMonthlySnapshot snapshot = snapshotRepository
                .findByMemberIdAndYearAndMonth(member.getId(), yearMonth.getYear(), yearMonth.getMonthValue())
                .orElseGet(() -> MemberMonthlySnapshot.builder().member(member)
                        .year(yearMonth.getYear()).month(yearMonth.getMonthValue()).build());

        snapshot.setTotalIncome(totals.income);
        snapshot.setTotalEssential(totals.essential);
        snapshot.setTotalDiscretionary(totals.discretionary);
        snapshot.setTotalInvestment(totals.investment);
        snapshot.setTotalDebtRepayment(totals.debtRepayment);
        snapshot.setTotalInsurance(totals.insurance);
        snapshot.setTotalInternalTransfer(totals.internalTransfer);
        snapshot.setTotalCashWithdrawal(totals.cashWithdrawal);
        snapshot.setTotalUnknown(totals.unknown);
        snapshot.setTotalExpenses(totalExpenses);
        BigDecimal savings = totals.income.subtract(totalExpenses);
        snapshot.setSavings(hasData ? savings : null);
        snapshot.setSavingsRate(totals.income.compareTo(BigDecimal.ZERO) > 0
                ? savings.divide(totals.income, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : null);
        snapshot.setTransactionCount(totals.transactionCount);
        snapshot.setAverageTransactionValue(totals.transactionCount > 0
                ? totals.sumAbsAmount.divide(BigDecimal.valueOf(totals.transactionCount), 2, RoundingMode.HALF_UP)
                : null);
        snapshot.setHasData(hasData);
        snapshot.setCalculatedAt(LocalDateTime.now());

        snapshotRepository.save(snapshot);
    }

    private void addToBucket(MonthTotals totals, Transaction txn) {
        String type = txn.getTransactionType();
        String bucket = type == null ? TransactionClassificationService.UNKNOWN : type.toUpperCase(Locale.ROOT);
        switch (bucket) {
            case TransactionClassificationService.INCOME -> totals.income = totals.income.add(txn.getAmount());
            case TransactionClassificationService.ESSENTIAL -> totals.essential = totals.essential.add(txn.getAmount());
            case TransactionClassificationService.DISCRETIONARY -> totals.discretionary = totals.discretionary.add(txn.getAmount());
            case TransactionClassificationService.INVESTMENT -> totals.investment = totals.investment.add(txn.getAmount());
            case TransactionClassificationService.DEBT_REPAYMENT -> totals.debtRepayment = totals.debtRepayment.add(txn.getAmount());
            case TransactionClassificationService.INSURANCE -> totals.insurance = totals.insurance.add(txn.getAmount());
            case TransactionClassificationService.INTERNAL_TRANSFER -> totals.internalTransfer = totals.internalTransfer.add(txn.getAmount().abs());
            case TransactionClassificationService.CASH_WITHDRAWAL -> totals.cashWithdrawal = totals.cashWithdrawal.add(txn.getAmount());
            default -> totals.unknown = totals.unknown.add(txn.getAmount());
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
