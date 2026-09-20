package com.prospr.app.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.prospr.app.entity.Member;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.TransactionRepository;

/**
 * Single entry point for the whole financial-analysis pipeline (Normalization -> Classification ->
 * Aggregation -> Financial Analysis -> Insight Generation), so Setu sync and manual import share
 * exactly one analytics code path instead of each hand-rolling its own subset of calls. Merchant
 * normalization happens inline inside {@link TransactionCategorizationService#categorize}, so it
 * isn't a separate stage here.
 *
 * <p>Never throws for a background-sync caller's sake would be nice, but each downstream stage
 * already owns its own failure handling ({@link LifestyleAnalysisService#recomputeIfEnabled} never
 * throws; {@link SafetyNetService#recomputeForMember} documents that callers triggered by background
 * sync must wrap it themselves) - this orchestrator doesn't add another swallow-everything layer on
 * top of that existing, already-reviewed behavior.
 */
@Service
public class FinancialAnalysisOrchestratorService {

    private final TransactionRepository transactionRepository;
    private final TransactionCategorizationService categorizationService;
    private final TransactionClassificationService classificationService;
    private final RecurringExpenseDetectionService recurringExpenseDetectionService;
    private final MonthlySnapshotService monthlySnapshotService;
    private final LifestyleAnalysisService lifestyleAnalysisService;
    private final SafetyNetService safetyNetService;

    public FinancialAnalysisOrchestratorService(TransactionRepository transactionRepository,
                                                 TransactionCategorizationService categorizationService,
                                                 TransactionClassificationService classificationService,
                                                 RecurringExpenseDetectionService recurringExpenseDetectionService,
                                                 MonthlySnapshotService monthlySnapshotService,
                                                 LifestyleAnalysisService lifestyleAnalysisService,
                                                 SafetyNetService safetyNetService) {
        this.transactionRepository = transactionRepository;
        this.categorizationService = categorizationService;
        this.classificationService = classificationService;
        this.recurringExpenseDetectionService = recurringExpenseDetectionService;
        this.monthlySnapshotService = monthlySnapshotService;
        this.lifestyleAnalysisService = lifestyleAnalysisService;
        this.safetyNetService = safetyNetService;
    }

    public void recomputeForMember(Member member) {
        List<Transaction> uncategorized = transactionRepository.findByMemberIdAndCategoryIsNull(member.getId());
        categorizationService.categorize(uncategorized);
        transactionRepository.saveAll(uncategorized);

        List<Transaction> all = transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId());
        classificationService.classify(all);
        transactionRepository.saveAll(all);

        recurringExpenseDetectionService.recomputeForMember(member);
        monthlySnapshotService.recomputeForMember(member);

        lifestyleAnalysisService.recomputeIfEnabled(member);
        safetyNetService.recomputeForMember(member);
    }
}
