package com.prospr.app.service;

import java.util.List;

import com.prospr.app.entity.Transaction;

/**
 * The named seam for "assign a transaction its category/classification" - deliberately batch-shaped
 * (not per-transaction) so an implementation can batch-load reference data once per call, the way
 * {@link TransactionCategorizationService} already does for {@link MerchantAlias} rows. {@link
 * RuleBasedTransactionClassifier} is the only implementation, and the only one intended: deterministic
 * rules, merchant/payee normalization, and each member's own confirmed mappings - no external service
 * call of any kind, by design. This interface exists so that guarantee is enforced at a single seam
 * (one implementation {@link FinancialAnalysisOrchestratorService} depends on) rather than scattered
 * assumptions throughout the codebase.
 */
public interface TransactionClassifier {

    /** Assigns category/subcategory/categoryConfidence/categorySource/merchantName - only to
     *  transactions that don't already have a category (existing, unchanged skip behavior). */
    void categorize(List<Transaction> transactions);

    /** Assigns transactionType (financial classification) and counterpartyType to every transaction
     *  in the list, and runs self-transfer detection across the batch. */
    void classify(List<Transaction> transactions);
}
