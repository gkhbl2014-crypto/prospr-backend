package com.prospr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import com.prospr.app.dto.request.ImportConfirmRequest;
import com.prospr.app.dto.request.ImportConfirmRow;
import com.prospr.app.dto.response.ImportConfirmResponse;
import com.prospr.app.dto.response.ImportPreviewResponse;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.TransactionRepository;
import com.prospr.app.service.CategoryTaxonomy;
import com.prospr.app.service.FinancialAnalysisOrchestratorService;
import com.prospr.app.service.TransactionCategorizationService;
import com.prospr.app.service.statement.ImportDuplicateDetectionService;
import com.prospr.app.service.statement.ParsedTransactionRow;
import com.prospr.app.service.statement.StatementParserRegistry;

@ExtendWith(MockitoExtension.class)
class TransactionImportControllerTest {

    @Mock
    private StatementParserRegistry parserRegistry;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private FinancialAnalysisOrchestratorService financialAnalysisOrchestratorService;
    @Mock
    private Authentication authentication;

    private final ImportDuplicateDetectionService duplicateDetectionService = new ImportDuplicateDetectionService();
    private final TransactionCategorizationService categorizationService =
            new TransactionCategorizationService(null, new com.prospr.app.service.CategoryFallbackRules(), null) {
                @Override
                public int categorize(List<Transaction> transactions) {
                    return 0; // no-op for this controller-level test - categorization itself is tested elsewhere
                }
            };

    private final CategoryTaxonomy categoryTaxonomy = new CategoryTaxonomy();

    private TransactionImportController controller() {
        return new TransactionImportController(parserRegistry, duplicateDetectionService, categorizationService,
                transactionRepository, memberRepository, financialAnalysisOrchestratorService, categoryTaxonomy);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).email("caller@example.com").build();
    }

    private void stubCaller(Member member) {
        when(authentication.getName()).thenReturn(member.getEmail());
        when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
    }

    @Test
    void previewNeverPersistsAnyRow() throws Exception {
        Member member = member();
        stubCaller(member);
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(List.of());
        when(parserRegistry.parse(any(), any(), any())).thenReturn(List.of(
                new ParsedTransactionRow(LocalDate.of(2023, 4, 1), "Zomato", null, null,
                        new BigDecimal("500.00"), new BigDecimal("45000.00"), "DEBIT", null)));

        MockMultipartFile file = new MockMultipartFile("file", "statement.csv", "text/csv", "data".getBytes());
        ImportPreviewResponse response = controller().preview(file, authentication).getBody();

        assertThat(response.getTotalParsed()).isEqualTo(1);
        assertThat(response.getRows().get(0).isPossibleDuplicate()).isFalse();
        verify(transactionRepository, never()).save(any());
        verify(transactionRepository, never()).saveAll(anyList());
        verifyNoInteractions(financialAnalysisOrchestratorService);
    }

    @Test
    void previewFlagsDuplicatesAgainstExistingTransactions() throws Exception {
        Member member = member();
        stubCaller(member);
        Transaction existing = Transaction.builder()
                .valueDate(LocalDate.of(2023, 4, 1)).type("DEBIT").amount(new BigDecimal("500.00")).source("SETU").build();
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId()))
                .thenReturn(List.of(existing));
        when(parserRegistry.parse(any(), any(), any())).thenReturn(List.of(
                new ParsedTransactionRow(LocalDate.of(2023, 4, 1), "Zomato", null, null,
                        new BigDecimal("500.00"), null, "DEBIT", null)));

        MockMultipartFile file = new MockMultipartFile("file", "statement.csv", "text/csv", "data".getBytes());
        ImportPreviewResponse response = controller().preview(file, authentication).getBody();

        assertThat(response.getRows().get(0).isPossibleDuplicate()).isTrue();
        assertThat(response.getRows().get(0).getDuplicateReason()).isEqualTo("MATCHES_DATE_AMOUNT_TYPE");
    }

    @Test
    void confirmPersistsSubmittedRowsWithManualSourceAndVisible() {
        Member member = member();
        stubCaller(member);
        when(transactionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ImportConfirmRow row = new ImportConfirmRow();
        row.setValueDate(LocalDate.of(2023, 4, 1));
        row.setNarration("Zomato");
        row.setType("DEBIT");
        row.setAmount(new BigDecimal("500.00"));
        ImportConfirmRequest request = new ImportConfirmRequest();
        request.setRows(List.of(row));

        ImportConfirmResponse response = controller().confirm(request, authentication).getBody();

        assertThat(response.getImported()).isEqualTo(1);
        assertThat(response.getTransactions().get(0).getSource()).isEqualTo("MANUAL");
        assertThat(response.getTransactions().get(0).isHidden()).isFalse();
        assertThat(response.getTransactions().get(0).getMaskedAccountNumber()).isNull();
    }

    @Test
    void confirmSetsTransactionTimestampFromValueDateSoRecencySortingWorks() {
        // transactions lists sort by transaction_timestamp DESC; Postgres puts NULLs first on a
        // DESC sort, so a manual row left without one would jump ahead of genuinely recent Setu
        // activity. Confirm must derive a timestamp from the file's value date instead.
        Member member = member();
        stubCaller(member);
        when(transactionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ImportConfirmRow row = new ImportConfirmRow();
        row.setValueDate(LocalDate.of(2023, 4, 1));
        row.setType("DEBIT");
        row.setAmount(new BigDecimal("500.00"));
        ImportConfirmRequest request = new ImportConfirmRequest();
        request.setRows(List.of(row));

        ImportConfirmResponse response = controller().confirm(request, authentication).getBody();

        assertThat(response.getTransactions().get(0).getTransactionTimestamp()).isNotNull();
        assertThat(response.getTransactions().get(0).getTransactionTimestamp().toLocalDate())
                .isEqualTo(LocalDate.of(2023, 4, 1));
    }

    @Test
    void confirmGivesSameDayRowsStrictlyIncreasingTimestampsMatchingFileOrder() {
        // "Current balance" is read off whichever transaction sorts most recent. If every same-day
        // row got an identical timestamp, Postgres would break ties arbitrarily instead of honoring
        // the statement's own chronological file order - picking a random same-day balance instead
        // of the true last one. Confirm must keep same-day rows in strictly increasing order.
        Member member = member();
        stubCaller(member);
        when(transactionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ImportConfirmRow first = new ImportConfirmRow();
        first.setValueDate(LocalDate.of(2026, 3, 7));
        first.setType("DEBIT");
        first.setAmount(new BigDecimal("119.00"));
        ImportConfirmRow second = new ImportConfirmRow();
        second.setValueDate(LocalDate.of(2026, 3, 7));
        second.setType("DEBIT");
        second.setAmount(new BigDecimal("114.00"));
        ImportConfirmRequest request = new ImportConfirmRequest();
        request.setRows(List.of(first, second));

        ImportConfirmResponse response = controller().confirm(request, authentication).getBody();

        assertThat(response.getTransactions().get(0).getTransactionTimestamp())
                .isBefore(response.getTransactions().get(1).getTransactionTimestamp());
    }

    @Test
    void confirmTriggersFinancialAnalysisRecompute() {
        Member member = member();
        stubCaller(member);
        when(transactionRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ImportConfirmRow row = new ImportConfirmRow();
        row.setValueDate(LocalDate.of(2023, 4, 1));
        row.setType("CREDIT");
        row.setAmount(new BigDecimal("1000.00"));
        ImportConfirmRequest request = new ImportConfirmRequest();
        request.setRows(List.of(row));

        controller().confirm(request, authentication);

        verify(financialAnalysisOrchestratorService, times(1)).recomputeForMember(member);
    }
}
