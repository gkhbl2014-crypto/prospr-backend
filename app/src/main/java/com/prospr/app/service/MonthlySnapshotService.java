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
import com.prospr.app.service.cache.AnalyticsCacheService;

/**
 * Builds the whole-month income/expense/savings picture ({@link MemberMonthlySnapshot}) for the
 * trailing {@link FinancialAnalysisProperties#getSnapshotHistoryMonths()} months, driven entirely by
 * {@code transactionType} ({@link TransactionClassificationService}) rather than raw category - so a
 * SETU transaction and a MANUAL import feed the exact same buckets. Distinct from
 * {@link MonthlySpendingSummaryService}, which only tracks per-category DEBIT sums for Lifestyle's
 * rolling baseline and has no concept of income or savings at all.
 *
 * <p>Every transaction lands in exactly one classification bucket ({@link TransactionClassificationService}
 * guarantees this via its {@code OTHER_CREDIT}/{@code OTHER_DEBIT} fallback), so two independent
 * totals - {@code rawCredits}/{@code rawDebits}, summed straight from each transaction's own
 * DEBIT/CREDIT {@code type} field, never from the classification buckets - must always equal the sum
 * of the classified buckets on their respective side. That equality (checked in
 * {@link #recomputeMonth}) is the reconciliation this class exists to guarantee: a bug in classification
 * can never silently make money disappear, because the raw and classified totals are computed two
 * different ways and compared.
 */
@Service
public class MonthlySnapshotService {

    private static final Logger log = LoggerFactory.getLogger(MonthlySnapshotService.class);
    private static final String DEBIT = "DEBIT";
    private static final String CREDIT = "CREDIT";
    /** Reconciliation must match exactly to the paisa - BigDecimal equality, not a tolerance band. */
    private static final BigDecimal RECONCILE_TOLERANCE = new BigDecimal("0.00");

    private final TransactionRepository transactionRepository;
    private final MemberMonthlySnapshotRepository snapshotRepository;
    private final FinancialAnalysisProperties properties;
    private final AnalyticsCacheService analyticsCacheService;
    private final EssentialCategoryCatalog essentialCategoryCatalog;

    public MonthlySnapshotService(TransactionRepository transactionRepository,
                                   MemberMonthlySnapshotRepository snapshotRepository,
                                   FinancialAnalysisProperties properties,
                                   AnalyticsCacheService analyticsCacheService,
                                   EssentialCategoryCatalog essentialCategoryCatalog) {
        this.transactionRepository = transactionRepository;
        this.snapshotRepository = snapshotRepository;
        this.properties = properties;
        this.analyticsCacheService = analyticsCacheService;
        this.essentialCategoryCatalog = essentialCategoryCatalog;
    }

    private static final class MonthTotals {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal refund = BigDecimal.ZERO;
        BigDecimal otherCredit = BigDecimal.ZERO;
        BigDecimal essential = BigDecimal.ZERO;
        BigDecimal discretionary = BigDecimal.ZERO;
        BigDecimal investment = BigDecimal.ZERO;
        BigDecimal debtRepayment = BigDecimal.ZERO;
        BigDecimal insurance = BigDecimal.ZERO;
        BigDecimal cashWithdrawal = BigDecimal.ZERO;
        BigDecimal otherDebit = BigDecimal.ZERO;
        BigDecimal internalTransferCredit = BigDecimal.ZERO;
        BigDecimal internalTransferDebit = BigDecimal.ZERO;
        BigDecimal unknown = BigDecimal.ZERO;
        BigDecimal rawCredits = BigDecimal.ZERO;
        BigDecimal rawDebits = BigDecimal.ZERO;
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
        analyticsCacheService.evictSpendingSummary(member.getId());
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
            if (CREDIT.equalsIgnoreCase(txn.getType())) {
                totals.rawCredits = totals.rawCredits.add(txn.getAmount());
            } else if (DEBIT.equalsIgnoreCase(txn.getType())) {
                totals.rawDebits = totals.rawDebits.add(txn.getAmount());
            }
            addToBucket(totals, txn);
        }

        BigDecimal totalExpenses = totals.essential.add(totals.discretionary);
        BigDecimal totalCashOutflow = totalExpenses.add(totals.investment).add(totals.debtRepayment)
                .add(totals.insurance).add(totals.cashWithdrawal).add(totals.otherDebit);
        boolean hasData = totals.transactionCount > 0;

        BigDecimal classifiedCredits = totals.income.add(totals.refund).add(totals.otherCredit)
                .add(totals.internalTransferCredit);
        BigDecimal classifiedDebits = totalCashOutflow.add(totals.internalTransferDebit).add(totals.unknown);
        BigDecimal creditGap = totals.rawCredits.subtract(classifiedCredits);
        BigDecimal debitGap = totals.rawDebits.subtract(classifiedDebits);
        BigDecimal difference = creditGap.add(debitGap);
        boolean reconciled = creditGap.abs().compareTo(RECONCILE_TOLERANCE) <= 0
                && debitGap.abs().compareTo(RECONCILE_TOLERANCE) <= 0;
        if (!reconciled) {
            log.warn("Financial snapshot for member '{}' {}-{} did not reconcile: rawCredits={} "
                            + "classifiedCredits={} rawDebits={} classifiedDebits={}",
                    member.getId(), yearMonth.getYear(), yearMonth.getMonthValue(),
                    totals.rawCredits, classifiedCredits, totals.rawDebits, classifiedDebits);
        }

        MemberMonthlySnapshot snapshot = snapshotRepository
                .findByMemberIdAndYearAndMonth(member.getId(), yearMonth.getYear(), yearMonth.getMonthValue())
                .orElseGet(() -> MemberMonthlySnapshot.builder().member(member)
                        .year(yearMonth.getYear()).month(yearMonth.getMonthValue()).build());

        snapshot.setTotalIncome(totals.income);
        snapshot.setTotalRefund(totals.refund);
        snapshot.setTotalOtherCredit(totals.otherCredit);
        snapshot.setTotalEssential(totals.essential);
        snapshot.setTotalDiscretionary(totals.discretionary);
        snapshot.setTotalInvestment(totals.investment);
        snapshot.setTotalDebtRepayment(totals.debtRepayment);
        snapshot.setTotalInsurance(totals.insurance);
        snapshot.setTotalInternalTransfer(totals.internalTransferCredit.add(totals.internalTransferDebit));
        snapshot.setTotalCashWithdrawal(totals.cashWithdrawal);
        snapshot.setTotalOtherDebit(totals.otherDebit);
        snapshot.setTotalUnknown(totals.unknown);
        snapshot.setTotalExpenses(totalExpenses);
        snapshot.setTotalCashOutflow(totalCashOutflow);
        BigDecimal savings = totals.income.subtract(totalCashOutflow);
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
        snapshot.setReconciled(reconciled);
        snapshot.setDifference(difference);
        snapshot.setCalculatedAt(LocalDateTime.now());

        snapshotRepository.save(snapshot);
    }

    private void addToBucket(MonthTotals totals, Transaction txn) {
        String type = txn.getTransactionType();
        String bucket = type == null ? TransactionClassificationService.UNKNOWN : type.toUpperCase(Locale.ROOT);
        switch (bucket) {
            case TransactionClassificationService.INCOME -> totals.income = totals.income.add(txn.getAmount());
            case TransactionClassificationService.REFUND -> totals.refund = totals.refund.add(txn.getAmount());
            case TransactionClassificationService.OTHER_CREDIT -> totals.otherCredit = totals.otherCredit.add(txn.getAmount());
            case TransactionClassificationService.EXPENSE -> {
                if (essentialCategoryCatalog.isEssential(txn.getEffectiveCategory())) {
                    totals.essential = totals.essential.add(txn.getAmount());
                } else {
                    totals.discretionary = totals.discretionary.add(txn.getAmount());
                }
            }
            case TransactionClassificationService.INVESTMENT -> totals.investment = totals.investment.add(txn.getAmount());
            case TransactionClassificationService.DEBT_REPAYMENT -> totals.debtRepayment = totals.debtRepayment.add(txn.getAmount());
            case TransactionClassificationService.INSURANCE -> totals.insurance = totals.insurance.add(txn.getAmount());
            case TransactionClassificationService.CASH_WITHDRAWAL -> totals.cashWithdrawal = totals.cashWithdrawal.add(txn.getAmount());
            case TransactionClassificationService.OTHER_DEBIT -> totals.otherDebit = totals.otherDebit.add(txn.getAmount());
            case TransactionClassificationService.INTERNAL_TRANSFER -> {
                if (CREDIT.equalsIgnoreCase(txn.getType())) {
                    totals.internalTransferCredit = totals.internalTransferCredit.add(txn.getAmount());
                } else {
                    totals.internalTransferDebit = totals.internalTransferDebit.add(txn.getAmount());
                }
            }
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
