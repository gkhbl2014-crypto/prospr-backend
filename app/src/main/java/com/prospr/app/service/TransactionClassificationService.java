package com.prospr.app.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.prospr.app.entity.Transaction;

/**
 * Assigns each transaction a coarse {@code transactionType} (Income/Essential/Discretionary/
 * Investment/Debt repayment/Insurance/Internal transfer/Cash withdrawal/Unknown) derived from its
 * effective category, then layers a self-account-transfer heuristic on top for members with
 * multiple linked Setu accounts.
 *
 * <p>Self-transfer detection is intentionally narrow: it requires at least two distinct linked
 * account numbers for the member (only possible via Setu - a manual PDF/CSV import never carries a
 * reliable {@code maskedAccountNumber}). A same-amount debit/credit pair a day apart between two of
 * a manual-only or single-account member's transactions is left independently classified - there is
 * no data in that case to tell a genuine transfer apart from two unrelated transactions that happen
 * to match, and guessing would risk hiding real spending or real income.
 */
@Service
public class TransactionClassificationService {

    public static final String INCOME = "INCOME";
    public static final String ESSENTIAL = "ESSENTIAL";
    public static final String DISCRETIONARY = "DISCRETIONARY";
    public static final String INVESTMENT = "INVESTMENT";
    public static final String DEBT_REPAYMENT = "DEBT_REPAYMENT";
    public static final String INSURANCE = "INSURANCE";
    public static final String INTERNAL_TRANSFER = "INTERNAL_TRANSFER";
    public static final String CASH_WITHDRAWAL = "CASH_WITHDRAWAL";
    public static final String UNKNOWN = "UNKNOWN";

    private static final String DEBIT = "DEBIT";
    private static final String CREDIT = "CREDIT";
    private static final Set<String> TRANSFER_MODES = Set.of("NEFT", "IMPS", "RTGS", "UPI");

    private static final Map<String, String> CATEGORY_TO_TYPE = buildCategoryToTypeMap();

    private final LifestyleCategoryCatalog lifestyleCategoryCatalog;

    public TransactionClassificationService(LifestyleCategoryCatalog lifestyleCategoryCatalog) {
        this.lifestyleCategoryCatalog = lifestyleCategoryCatalog;
    }

    public void classify(List<Transaction> transactions) {
        for (Transaction txn : transactions) {
            txn.setTransactionType(resolveType(txn));
        }
        detectSelfTransfers(transactions);
    }

    private String resolveType(Transaction txn) {
        String category = txn.getEffectiveCategory();
        if (category == null) {
            return UNKNOWN;
        }
        String upper = category.toUpperCase(Locale.ROOT);
        if (CATEGORY_TO_TYPE.containsKey(upper)) {
            return CATEGORY_TO_TYPE.get(upper);
        }
        return lifestyleCategoryCatalog.isLifestyleCategory(upper) ? DISCRETIONARY : UNKNOWN;
    }

    /**
     * Pairs a DEBIT (transfer-capable mode) on one linked account with a same-amount CREDIT on a
     * different linked account of the same member, within a day - marks both INTERNAL_TRANSFER,
     * overriding whatever category-derived type they resolved to above. Only attempted when the
     * member has at least 2 distinct non-null masked account numbers among their transactions.
     */
    private void detectSelfTransfers(List<Transaction> transactions) {
        Set<String> distinctAccounts = transactions.stream()
                .map(Transaction::getMaskedAccountNumber)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (distinctAccounts.size() < 2) {
            return;
        }

        List<Transaction> debits = transactions.stream()
                .filter(t -> DEBIT.equalsIgnoreCase(t.getType()))
                .filter(t -> t.getMode() != null && TRANSFER_MODES.contains(t.getMode().toUpperCase(Locale.ROOT)))
                .filter(t -> t.getMaskedAccountNumber() != null && t.getAmount() != null && t.getValueDate() != null)
                .toList();
        List<Transaction> credits = new java.util.ArrayList<>(transactions.stream()
                .filter(t -> CREDIT.equalsIgnoreCase(t.getType()))
                .filter(t -> t.getMaskedAccountNumber() != null && t.getAmount() != null && t.getValueDate() != null)
                .toList());

        for (Transaction debit : debits) {
            for (Transaction credit : credits) {
                if (credit.getMaskedAccountNumber().equals(debit.getMaskedAccountNumber())) {
                    continue;
                }
                if (debit.getAmount().compareTo(credit.getAmount()) != 0) {
                    continue;
                }
                LocalDate debitDate = debit.getValueDate();
                LocalDate creditDate = credit.getValueDate();
                boolean sameOrNextDay = creditDate.equals(debitDate) || creditDate.equals(debitDate.plusDays(1));
                if (!sameOrNextDay) {
                    continue;
                }
                debit.setTransactionType(INTERNAL_TRANSFER);
                credit.setTransactionType(INTERNAL_TRANSFER);
                credits.remove(credit);
                break;
            }
        }
    }

    private static Map<String, String> buildCategoryToTypeMap() {
        Map<String, String> map = new HashMap<>();
        map.put("RENT", ESSENTIAL);
        map.put("GROCERIES", ESSENTIAL);
        map.put("UTILITIES", ESSENTIAL);
        map.put("MEDICAL", ESSENTIAL);
        map.put("EDUCATION", ESSENTIAL);
        map.put("TRANSPORTATION", ESSENTIAL);
        map.put("FUEL", ESSENTIAL);
        map.put("TAX", ESSENTIAL);
        map.put("EMI", DEBT_REPAYMENT);
        map.put("LOAN_PAYMENT", DEBT_REPAYMENT);
        map.put("INSURANCE", INSURANCE);
        map.put("INVESTMENT", INVESTMENT);
        map.put("CASH_WITHDRAWAL", CASH_WITHDRAWAL);
        map.put("CREDIT_CARD_PAYMENT", INTERNAL_TRANSFER);
        map.put("SALARY_INCOME", INCOME);
        return map;
    }
}
