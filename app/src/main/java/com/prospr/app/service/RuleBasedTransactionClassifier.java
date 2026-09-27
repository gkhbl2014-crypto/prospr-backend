package com.prospr.app.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.prospr.app.entity.Transaction;

/**
 * The only {@link TransactionClassifier} implementation - entirely deterministic, no external service
 * calls of any kind. Coordinates {@link TransactionCategorizationService} (unchanged), {@link
 * TransactionClassificationService} (unchanged), {@link CounterpartyTypeClassifier} (new: P2P/merchant/
 * unknown), and {@link PayeePurposeClassifier} (new: applies the member's own learned payee purposes)
 * in one named sequence, giving callers one seam to depend on instead of four services directly.
 */
@Component
public class RuleBasedTransactionClassifier implements TransactionClassifier {

    private final TransactionCategorizationService categorizationService;
    private final TransactionClassificationService classificationService;
    private final CounterpartyTypeClassifier counterpartyTypeClassifier;
    private final PayeePurposeClassifier payeePurposeClassifier;

    public RuleBasedTransactionClassifier(TransactionCategorizationService categorizationService,
                                           TransactionClassificationService classificationService,
                                           CounterpartyTypeClassifier counterpartyTypeClassifier,
                                           PayeePurposeClassifier payeePurposeClassifier) {
        this.categorizationService = categorizationService;
        this.classificationService = classificationService;
        this.counterpartyTypeClassifier = counterpartyTypeClassifier;
        this.payeePurposeClassifier = payeePurposeClassifier;
    }

    @Override
    public void categorize(List<Transaction> transactions) {
        categorizationService.categorize(transactions);
    }

    @Override
    public void classify(List<Transaction> transactions) {
        classificationService.classify(transactions);
        // Runs on every transaction on every pass (not just newly-categorized ones) so historical
        // transactions get backfilled and a category cleared by /direction's reset is re-evaluated.
        // Counterparty type is derived from the categorization-only state above, BEFORE a learned
        // purpose can add a category - a P2P payment tagged "Household" is still factually P2P, only
        // now resolved, so purpose must never influence this determination.
        for (Transaction txn : transactions) {
            txn.setCounterpartyType(counterpartyTypeClassifier.classify(txn));
        }
        payeePurposeClassifier.applyLearnedPurposes(transactions);
    }
}
