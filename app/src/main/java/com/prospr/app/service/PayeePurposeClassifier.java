package com.prospr.app.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberTransactionLabel;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.MemberTransactionLabelRepository;

/**
 * Applies a member's own learned payee purposes ({@link PayeePurpose}) to transactions categorization
 * couldn't otherwise resolve - the "how do you usually use payments to Rahul" answer feeding back into
 * the pipeline. Deliberately conservative: only acts on the subset of purposes that map unambiguously
 * onto an existing synthetic category - the exact same {@code MARKED_ESSENTIAL}/{@code
 * MARKED_LIFESTYLE_CREEP}/{@code INVESTMENT} categories {@link
 * com.prospr.app.controller.TransactionController}'s manual {@code /tag} endpoint already uses, so a
 * payee-level purpose and an individual manual tag land in the identical downstream bucket.
 * PERSONAL_TRANSFER, REPAYMENT and OTHER are left as pure context - {@link
 * TransactionClassificationService}'s direction-based OTHER_CREDIT/OTHER_DEBIT fallback for an
 * uncategorized transaction is already correct for all three; forcing a category onto them would
 * misrepresent a transfer/repayment as consumption.
 *
 * <p>Never touches a transaction with its own individual {@code userCategoryOverride} (the existing
 * {@code /tag} endpoint always wins - this is exactly the "must not silently rewrite a user-confirmed
 * classification" requirement), and never touches one whose category came from a real merchant/keyword
 * match. Only ever writes a category it has itself marked {@code categorySource=LEARNED_PURPOSE}, so a
 * later change to the same payee's purpose can be safely re-applied without clobbering anything else.
 */
@Component
public class PayeePurposeClassifier {

    public static final String SOURCE_LEARNED_PURPOSE = "LEARNED_PURPOSE";

    private static final Map<String, String> PURPOSE_TO_CATEGORY = Map.of(
            PayeePurpose.HOUSEHOLD, EssentialCategoryCatalog.MARKED_ESSENTIAL,
            PayeePurpose.SHARED_EXPENSE, LifestyleCategoryCatalog.MARKED_LIFESTYLE_CREEP,
            PayeePurpose.GIFT, LifestyleCategoryCatalog.MARKED_LIFESTYLE_CREEP,
            PayeePurpose.INVESTMENT, "INVESTMENT");

    private static final Map<String, String> CATEGORY_TO_TRANSACTION_TYPE = Map.of(
            EssentialCategoryCatalog.MARKED_ESSENTIAL, TransactionClassificationService.EXPENSE,
            LifestyleCategoryCatalog.MARKED_LIFESTYLE_CREEP, TransactionClassificationService.EXPENSE,
            "INVESTMENT", TransactionClassificationService.INVESTMENT);

    private static final Set<String> SAFE_TO_OVERWRITE_SOURCES = Set.of(SOURCE_LEARNED_PURPOSE);

    private final MemberTransactionLabelRepository labelRepository;
    private final CounterpartyKeyResolver counterpartyKeyResolver;

    public PayeePurposeClassifier(MemberTransactionLabelRepository labelRepository,
                                   CounterpartyKeyResolver counterpartyKeyResolver) {
        this.labelRepository = labelRepository;
        this.counterpartyKeyResolver = counterpartyKeyResolver;
    }

    /** transactions must all belong to the same member (true of every call site today - the
     *  classification pipeline always runs per-member). No-op for an empty list. */
    public void applyLearnedPurposes(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return;
        }
        Member member = transactions.get(0).getMember();
        Map<String, String> purposeByCounterpartyKey = labelRepository.findByMemberId(member.getId()).stream()
                .filter(label -> label.getPurpose() != null && PURPOSE_TO_CATEGORY.containsKey(label.getPurpose()))
                .collect(Collectors.toMap(MemberTransactionLabel::getCounterpartyKey, MemberTransactionLabel::getPurpose,
                        (first, second) -> first));
        if (purposeByCounterpartyKey.isEmpty()) {
            return;
        }

        for (Transaction txn : transactions) {
            if (txn.getUserCategoryOverride() != null) {
                continue;
            }
            // Set.of(...).contains(null) throws NPE (unlike a regular HashSet) - a transaction can
            // genuinely have a non-null category with a null categorySource (e.g. categorized before
            // categorySource was consistently populated), so this must be checked explicitly.
            String categorySource = txn.getCategorySource();
            boolean safeToOverwrite = txn.getCategory() == null
                    || (categorySource != null && SAFE_TO_OVERWRITE_SOURCES.contains(categorySource));
            if (!safeToOverwrite) {
                continue;
            }
            String counterpartyKey = counterpartyKeyResolver.resolveKey(txn).orElse(null);
            String purpose = counterpartyKey == null ? null : purposeByCounterpartyKey.get(counterpartyKey);
            if (purpose == null) {
                continue;
            }
            String category = PURPOSE_TO_CATEGORY.get(purpose);
            txn.setCategory(category);
            txn.setCategorySource(SOURCE_LEARNED_PURPOSE);
            txn.setCategoryConfidence(TransactionCategorizationService.CONFIDENCE_MEDIUM);
            txn.setTransactionType(CATEGORY_TO_TRANSACTION_TYPE.get(category));
        }
    }
}
