package com.prospr.app.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.entity.Member;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class FinancialAnalysisOrchestratorServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionCategorizationService categorizationService;
    @Mock
    private TransactionClassificationService classificationService;
    @Mock
    private RecurringExpenseDetectionService recurringExpenseDetectionService;
    @Mock
    private MonthlySnapshotService monthlySnapshotService;
    @Mock
    private LifestyleAnalysisService lifestyleAnalysisService;
    @Mock
    private SafetyNetService safetyNetService;

    private FinancialAnalysisOrchestratorService service() {
        return new FinancialAnalysisOrchestratorService(transactionRepository, categorizationService,
                classificationService, recurringExpenseDetectionService, monthlySnapshotService,
                lifestyleAnalysisService, safetyNetService);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).build();
    }

    @Test
    void runsEveryPipelineStageInOrderForOneMember() {
        Member member = member();
        List<Transaction> uncategorized = List.of(Transaction.builder().build());
        List<Transaction> all = List.of(Transaction.builder().build(), Transaction.builder().build());
        when(transactionRepository.findByMemberIdAndCategoryIsNull(member.getId())).thenReturn(uncategorized);
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(all);

        service().recomputeForMember(member);

        InOrder order = inOrder(categorizationService, transactionRepository, classificationService,
                recurringExpenseDetectionService, monthlySnapshotService, lifestyleAnalysisService, safetyNetService);
        order.verify(categorizationService).categorize(uncategorized);
        order.verify(transactionRepository).saveAll(uncategorized);
        order.verify(classificationService).classify(all);
        order.verify(transactionRepository).saveAll(all);
        order.verify(recurringExpenseDetectionService).recomputeForMember(member);
        order.verify(monthlySnapshotService).recomputeForMember(member);
        order.verify(lifestyleAnalysisService).recomputeIfEnabled(member);
        order.verify(safetyNetService).recomputeForMember(member);
    }

    @Test
    void safetyNetStillRunsWhenLifestyleRecomputeIsANoOp() {
        Member member = member();
        when(transactionRepository.findByMemberIdAndCategoryIsNull(member.getId())).thenReturn(List.of());
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(List.of());

        service().recomputeForMember(member);

        verify(safetyNetService, times(1)).recomputeForMember(member);
    }
}
