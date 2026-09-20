package com.prospr.app.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.prospr.app.dto.request.ImportConfirmRequest;
import com.prospr.app.dto.request.ImportConfirmRow;
import com.prospr.app.dto.response.ImportConfirmResponse;
import com.prospr.app.dto.response.ImportPreviewResponse;
import com.prospr.app.dto.response.ImportPreviewRow;
import com.prospr.app.dto.response.TransactionResponse;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.Transaction;
import com.prospr.app.exception.ImportValidationException;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.TransactionRepository;
import com.prospr.app.service.CategoryTaxonomy;
import com.prospr.app.service.FinancialAnalysisOrchestratorService;
import com.prospr.app.service.TransactionCategorizationService;
import com.prospr.app.service.statement.ImportDuplicateDetectionService;
import com.prospr.app.service.statement.ImportDuplicateDetectionService.DuplicateCheck;
import com.prospr.app.service.statement.ParsedTransactionRow;
import com.prospr.app.service.statement.StatementParserRegistry;

/**
 * Stateless preview -> confirm import of a bank statement (PDF/CSV/XLSX). No file is ever
 * persisted - it's parsed synchronously in-memory and discarded, which trivially satisfies "not
 * publicly accessible" since nothing is ever stored to be accessed. No server-side staging of
 * parsed rows either: the frontend holds the preview client-side and echoes back whatever the user
 * confirmed (after any edits/deselection) to /confirm.
 */
@RestController
@RequestMapping("/api/transactions/import")
public class TransactionImportController {

    private static final String MANUAL_SOURCE = "MANUAL";

    private final StatementParserRegistry parserRegistry;
    private final ImportDuplicateDetectionService duplicateDetectionService;
    private final TransactionCategorizationService categorizationService;
    private final TransactionRepository transactionRepository;
    private final MemberRepository memberRepository;
    private final FinancialAnalysisOrchestratorService financialAnalysisOrchestratorService;
    private final CategoryTaxonomy categoryTaxonomy;

    public TransactionImportController(StatementParserRegistry parserRegistry,
                                        ImportDuplicateDetectionService duplicateDetectionService,
                                        TransactionCategorizationService categorizationService,
                                        TransactionRepository transactionRepository,
                                        MemberRepository memberRepository,
                                        FinancialAnalysisOrchestratorService financialAnalysisOrchestratorService,
                                        CategoryTaxonomy categoryTaxonomy) {
        this.parserRegistry = parserRegistry;
        this.duplicateDetectionService = duplicateDetectionService;
        this.categorizationService = categorizationService;
        this.transactionRepository = transactionRepository;
        this.memberRepository = memberRepository;
        this.financialAnalysisOrchestratorService = financialAnalysisOrchestratorService;
        this.categoryTaxonomy = categoryTaxonomy;
    }

    @PostMapping("/preview")
    public ResponseEntity<ImportPreviewResponse> preview(@RequestParam("file") MultipartFile file,
                                                           Authentication authentication) {
        Member member = resolveMember(authentication);
        if (file == null || file.isEmpty()) {
            throw new ImportValidationException("No file was uploaded");
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException ex) {
            throw new ImportValidationException("Unable to read the uploaded file", ex);
        }

        List<ParsedTransactionRow> parsedRows =
                parserRegistry.parse(file.getOriginalFilename(), file.getContentType(), fileBytes);
        List<Transaction> existingTransactions =
                transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId());

        List<ImportPreviewRow> previewRows = parsedRows.stream()
                .map(row -> toPreviewRow(row, existingTransactions))
                .toList();

        return ResponseEntity.ok(ImportPreviewResponse.builder()
                .rows(previewRows)
                .totalParsed(previewRows.size())
                .build());
    }

    @PostMapping("/confirm")
    public ResponseEntity<ImportConfirmResponse> confirm(@Valid @RequestBody ImportConfirmRequest request,
                                                           Authentication authentication) {
        Member member = resolveMember(authentication);

        List<ImportConfirmRow> rows = request.getRows();
        List<Transaction> newTransactions = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            newTransactions.add(toTransaction(rows.get(i), member, i));
        }

        categorizationService.categorize(newTransactions);
        List<Transaction> saved = transactionRepository.saveAll(newTransactions);

        financialAnalysisOrchestratorService.recomputeForMember(member);

        List<TransactionResponse> responses = saved.stream().map(this::toTransactionResponse).toList();
        return ResponseEntity.ok(ImportConfirmResponse.builder()
                .imported(responses.size())
                .transactions(responses)
                .build());
    }

    private ImportPreviewRow toPreviewRow(ParsedTransactionRow row, List<Transaction> existingTransactions) {
        DuplicateCheck duplicateCheck = duplicateDetectionService.check(row, existingTransactions);
        return ImportPreviewRow.builder()
                .clientRowId(UUID.randomUUID().toString())
                .valueDate(row.valueDate())
                .narration(row.narration())
                .debit(row.debit())
                .credit(row.credit())
                .amount(row.amount())
                .type(row.type())
                .balance(row.balance())
                .reference(row.reference())
                .possibleDuplicate(duplicateCheck.possibleDuplicate())
                .duplicateReason(duplicateCheck.duplicateReason())
                .build();
    }

    private Transaction toTransaction(ImportConfirmRow row, Member member, int sequenceInBatch) {
        return Transaction.builder()
                .member(member)
                .maskedAccountNumber(null)
                // A PDF/CSV/XLSX line doesn't reliably carry a bank-assigned transaction id the way
                // Setu's XML does; txn_id is NOT NULL, so synthesize one rather than relaxing the
                // column - "reference" (nullable, separate) still carries whatever the file printed.
                .txnId("MANUAL-" + UUID.randomUUID())
                .type(row.getType())
                .amount(row.getAmount())
                .transactionalBalance(row.getBalance())
                .narration(row.getNarration())
                .reference(row.getReference())
                .valueDate(row.getValueDate())
                // Every transaction list (dashboard, family feed, "All Transactions") sorts by
                // transaction_timestamp, and "current balance" is read off whichever transaction
                // sorts most recent. A file line has no real time-of-day, but giving every same-day
                // row an identical midnight timestamp would make Postgres break ties arbitrarily
                // among them - picking a random same-day balance instead of the statement's true
                // last one. The statement lists rows in chronological file order (confirmed by its
                // own sequential serial numbers), so a monotonically increasing per-row offset off
                // that same midnight preserves that order without needing real clock times.
                .transactionTimestamp(
                        row.getValueDate().atStartOfDay().atOffset(java.time.ZoneOffset.UTC).plusSeconds(sequenceInBatch))
                .isHidden(false)
                .source(MANUAL_SOURCE)
                .build();
    }

    private TransactionResponse toTransactionResponse(Transaction txn) {
        String effectiveCategory = txn.getEffectiveCategory();
        return TransactionResponse.builder()
                .id(txn.getId())
                .memberId(txn.getMember().getId())
                .maskedAccountNumber(txn.getMaskedAccountNumber())
                .txnId(txn.getTxnId())
                .mode(txn.getMode())
                .type(txn.getType())
                .amount(txn.getAmount())
                .transactionalBalance(txn.getTransactionalBalance())
                .narration(txn.getNarration())
                .reference(txn.getReference())
                .valueDate(txn.getValueDate())
                .transactionTimestamp(txn.getTransactionTimestamp())
                .hidden(Boolean.TRUE.equals(txn.getIsHidden()))
                .source(txn.getSource())
                .effectiveCategory(effectiveCategory)
                .topLevelCategory(categoryTaxonomy.topLevelName(effectiveCategory))
                .categoryConfidence(txn.getCategoryConfidence())
                .transactionType(txn.getTransactionType())
                .build();
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
