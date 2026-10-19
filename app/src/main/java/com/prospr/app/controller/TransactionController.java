package com.prospr.app.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.request.UpdatePayeePurposeRequest;
import com.prospr.app.dto.request.UpdateTransactionDirectionRequest;
import com.prospr.app.dto.request.UpdateTransactionHiddenRequest;
import com.prospr.app.dto.request.UpdateTransactionLabelRequest;
import com.prospr.app.dto.request.UpdateTransactionTagRequest;
import com.prospr.app.dto.response.PayeePurposeResponse;
import com.prospr.app.dto.response.ReviewQueueEntryResponse;
import com.prospr.app.dto.response.TransactionResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberTransactionLabel;
import com.prospr.app.entity.Transaction;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.exception.TransactionTagException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.MemberTransactionLabelRepository;
import com.prospr.app.repository.TransactionRepository;
import com.prospr.app.service.CategoryTaxonomy;
import com.prospr.app.service.CounterpartyKeyResolver;
import com.prospr.app.service.EssentialCategoryCatalog;
import com.prospr.app.service.FinancialAnalysisOrchestratorService;
import com.prospr.app.service.LifestyleCategoryCatalog;
import com.prospr.app.service.PayeePurpose;
import com.prospr.app.service.ReviewQueueService;
import com.prospr.app.service.TransactionCategorizationService;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private static final String TAG_AUTO = "AUTO";

    /** Maps each user-facing tag (other than AUTO, which clears the override outright) to the
     *  synthetic/real category stored as userCategoryOverride. */
    private static final Map<String, String> TAG_TO_CATEGORY_OVERRIDE = Map.of(
            "INVESTMENT", "INVESTMENT",
            "ESSENTIAL", EssentialCategoryCatalog.MARKED_ESSENTIAL,
            "LIFESTYLE_CREEP", LifestyleCategoryCatalog.MARKED_LIFESTYLE_CREEP);

    private final TransactionRepository transactionRepository;
    private final MemberRepository memberRepository;
    private final MemberTransactionLabelRepository labelRepository;
    private final CategoryTaxonomy categoryTaxonomy;
    private final CounterpartyKeyResolver counterpartyKeyResolver;
    private final ReviewQueueService reviewQueueService;
    private final FinancialAnalysisOrchestratorService financialAnalysisOrchestratorService;

    public TransactionController(TransactionRepository transactionRepository, MemberRepository memberRepository,
                                  MemberTransactionLabelRepository labelRepository,
                                  CategoryTaxonomy categoryTaxonomy, CounterpartyKeyResolver counterpartyKeyResolver,
                                  ReviewQueueService reviewQueueService,
                                  FinancialAnalysisOrchestratorService financialAnalysisOrchestratorService) {
        this.transactionRepository = transactionRepository;
        this.memberRepository = memberRepository;
        this.labelRepository = labelRepository;
        this.categoryTaxonomy = categoryTaxonomy;
        this.counterpartyKeyResolver = counterpartyKeyResolver;
        this.reviewQueueService = reviewQueueService;
        this.financialAnalysisOrchestratorService = financialAnalysisOrchestratorService;
    }

    /**
     * Returns every transaction in the caller's family, including ones an owner has marked hidden -
     * each row is flagged via {@code hidden} rather than filtered out here, so that any aggregate
     * computed from this same list (net income/expenditure, balances, insights) keeps counting
     * hidden transactions. Only list-style UI (the shared activity feed) should filter on that flag.
     */
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> listTransactions(Authentication authentication) {
        Member member = resolveMember(authentication);
        Family family = member.getFamily();
        Map<String, MemberTransactionLabel> labels = loadLabelsByCounterpartyKey(member.getId());

        List<TransactionResponse> transactions = (family == null
                ? transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())
                : transactionRepository.findByMemberFamilyIdOrderByTransactionTimestampDesc(family.getId()))
                .stream()
                .map(txn -> toResponse(txn, labels))
                .toList();

        return ResponseEntity.ok(transactions);
    }

    /** Self-only: a member can only hide/unhide their own transactions, resolved via findByIdAndMemberId. */
    @PutMapping("/{id}/visibility")
    public ResponseEntity<TransactionResponse> updateHidden(@PathVariable UUID id,
                                                              @Valid @RequestBody UpdateTransactionHiddenRequest request,
                                                              Authentication authentication) {
        Member member = resolveMember(authentication);
        Transaction txn = transactionRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        txn.setIsHidden(request.getHidden());
        transactionRepository.save(txn);

        return ResponseEntity.ok(toResponse(txn, loadLabelsByCounterpartyKey(member.getId())));
    }

    /**
     * Self-only: a member can only retag their own transactions. Setting a tag other than AUTO
     * writes a {@code userCategoryOverride} that {@link Transaction#getEffectiveCategory()} then
     * prefers everywhere downstream - re-running the whole financial-analysis pipeline here (same as
     * the manual "Recalculate" action) is what makes the correction actually show up in Monthly
     * Trend/Lifestyle/Safety Net immediately, rather than only on the next unrelated recompute.
     */
    @PutMapping("/{id}/tag")
    public ResponseEntity<TransactionResponse> updateTag(@PathVariable UUID id,
                                                           @Valid @RequestBody UpdateTransactionTagRequest request,
                                                           Authentication authentication) {
        Member member = resolveMember(authentication);
        Transaction txn = transactionRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        String tag = request.getTag() == null ? "" : request.getTag().toUpperCase();
        if (TAG_AUTO.equals(tag)) {
            // Reverts to the system's own derived category/source - never leave a stale
            // USER_OVERRIDE source lying around once the override itself is gone.
            txn.setUserCategoryOverride(null);
        } else if (TAG_TO_CATEGORY_OVERRIDE.containsKey(tag)) {
            txn.setUserCategoryOverride(TAG_TO_CATEGORY_OVERRIDE.get(tag));
            txn.setCategorySource(TransactionCategorizationService.SOURCE_USER_OVERRIDE);
        } else {
            throw new TransactionTagException(
                    "Unrecognized tag '" + request.getTag() + "' - expected one of AUTO, "
                            + String.join(", ", TAG_TO_CATEGORY_OVERRIDE.keySet()));
        }
        txn.setUserOverrideAt(LocalDateTime.now());
        transactionRepository.save(txn);

        financialAnalysisOrchestratorService.recomputeForMember(member);

        Transaction updated = transactionRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        return ResponseEntity.ok(toResponse(updated, loadLabelsByCounterpartyKey(member.getId())));
    }

    /**
     * Self-only: corrects a transaction's CREDIT/DEBIT direction - for when the source statement's
     * own parsing got it wrong (e.g. {@code GenericPdfStatementParser}'s documented gap: the very
     * first row of an imported file has no prior balance to infer direction from, and defaults to
     * DEBIT). Category/classification are reset to null rather than left stale, since a category the
     * categorization pipeline assigned for one direction (e.g. "SHOPPING" on a debit) is meaningless
     * once the direction flips - {@link FinancialAnalysisOrchestratorService#recomputeForMember} then
     * re-derives a fresh, direction-appropriate category and classification from scratch.
     */
    @PutMapping("/{id}/direction")
    public ResponseEntity<TransactionResponse> updateDirection(@PathVariable UUID id,
                                                                 @Valid @RequestBody UpdateTransactionDirectionRequest request,
                                                                 Authentication authentication) {
        Member member = resolveMember(authentication);
        Transaction txn = transactionRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        txn.setType(request.getType().toUpperCase());
        txn.setCategory(null);
        txn.setSubcategory(null);
        txn.setCategoryConfidence(null);
        txn.setCategorySource(null);
        txn.setUserCategoryOverride(null);
        txn.setUserOverrideAt(LocalDateTime.now());
        transactionRepository.save(txn);

        financialAnalysisOrchestratorService.recomputeForMember(member);

        Transaction updated = transactionRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));
        return ResponseEntity.ok(toResponse(updated, loadLabelsByCounterpartyKey(member.getId())));
    }

    /**
     * Self-only: attaches (or clears, via a blank/absent label) the caller's own personal note for
     * this transaction's counterparty - every past and future transaction that resolves to the same
     * {@link CounterpartyKeyResolver} key picks it up automatically on next read, not just this one
     * row. Purely a display annotation: no category/classification/amount is touched, so unlike
     * {@code /tag} and {@code /direction} this never needs a full pipeline recompute.
     */
    @PutMapping("/{id}/label")
    public ResponseEntity<TransactionResponse> updateLabel(@PathVariable UUID id,
                                                             @Valid @RequestBody UpdateTransactionLabelRequest request,
                                                             Authentication authentication) {
        Member member = resolveMember(authentication);
        Transaction txn = transactionRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        String counterpartyKey = counterpartyKeyResolver.resolveKey(txn)
                .orElseThrow(() -> new TransactionTagException(
                        "This transaction has no narration or merchant name to identify its counterparty by"));

        String label = request.getLabel() == null ? null : request.getLabel().trim();
        if (label == null || label.isEmpty()) {
            // A row can also carry `purpose` - only delete it outright when purpose is empty too,
            // otherwise just clear the free-text note and leave the learned purpose intact.
            labelRepository.findByMemberIdAndCounterpartyKey(member.getId(), counterpartyKey).ifPresent(entity -> {
                if (entity.getPurpose() == null) {
                    labelRepository.delete(entity);
                } else {
                    entity.setLabel(null);
                    labelRepository.save(entity);
                }
            });
        } else {
            MemberTransactionLabel entity = labelRepository
                    .findByMemberIdAndCounterpartyKey(member.getId(), counterpartyKey)
                    .orElseGet(() -> MemberTransactionLabel.builder()
                            .member(member).counterpartyKey(counterpartyKey).evidenceCount(0).build());
            entity.setLabel(label);
            labelRepository.save(entity);
        }

        return ResponseEntity.ok(toResponse(txn, loadLabelsByCounterpartyKey(member.getId())));
    }

    /**
     * Self-only: the "how do you usually use payments to X" answer - resolves every one of the
     * caller's transactions that share this counterparty at once (see {@link PayeePurposeClassifier}),
     * not just one row. Triggers a full pipeline recompute, since - unlike the free-text label - a
     * purpose can change category/transactionType for potentially many transactions.
     */
    @PutMapping("/payee/purpose")
    public ResponseEntity<PayeePurposeResponse> updatePayeePurpose(@Valid @RequestBody UpdatePayeePurposeRequest request,
                                                                     Authentication authentication) {
        Member member = resolveMember(authentication);
        String purpose = request.getPurpose() == null ? "" : request.getPurpose().toUpperCase();
        if (!PayeePurpose.ALL.contains(purpose)) {
            throw new TransactionTagException(
                    "Unrecognized purpose '" + request.getPurpose() + "' - expected one of "
                            + String.join(", ", PayeePurpose.ALL));
        }
        String counterpartyKey = request.getCounterpartyKey();

        int affectedCount = (int) transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())
                .stream()
                .filter(txn -> counterpartyKeyResolver.resolveKey(txn).map(counterpartyKey::equals).orElse(false))
                .count();
        if (affectedCount == 0) {
            throw new ResourceNotFoundException("No transactions found for this counterparty");
        }

        MemberTransactionLabel entity = labelRepository.findByMemberIdAndCounterpartyKey(member.getId(), counterpartyKey)
                .orElseGet(() -> MemberTransactionLabel.builder().member(member).counterpartyKey(counterpartyKey).build());
        entity.setPurpose(purpose);
        entity.setEvidenceCount(affectedCount);
        String label = request.getLabel() == null ? null : request.getLabel().trim();
        if (label != null && !label.isEmpty()) {
            entity.setLabel(label);
        }
        labelRepository.save(entity);

        financialAnalysisOrchestratorService.recomputeForMember(member);

        return ResponseEntity.ok(PayeePurposeResponse.builder()
                .counterpartyKey(counterpartyKey)
                .displayName(counterpartyKeyResolver.toDisplayName(counterpartyKey))
                .purpose(purpose)
                .label(entity.getLabel())
                .affectedTransactionCount(affectedCount)
                .build());
    }

    /**
     * Self-only: "N transactions may need your help" - counterparties categorization couldn't resolve,
     * grouped and ranked by how much they materially affect the caller's totals, not one row per
     * transaction. See {@link ReviewQueueService} for the ranking/materiality logic.
     */
    @GetMapping("/review-queue")
    public ResponseEntity<List<ReviewQueueEntryResponse>> reviewQueue(Authentication authentication) {
        Member member = resolveMember(authentication);
        List<ReviewQueueEntryResponse> queue = reviewQueueService.buildQueue(member).stream()
                .map(entry -> ReviewQueueEntryResponse.builder()
                        .counterpartyKey(entry.counterpartyKey())
                        .displayName(entry.displayName())
                        .direction(entry.direction())
                        .transactionCount(entry.transactionCount())
                        .totalAmount(entry.totalAmount())
                        .earliestDate(entry.earliestDate())
                        .latestDate(entry.latestDate())
                        .patternOccurrences(entry.evidence().cadenceGuess() == null && entry.evidence().typicalAmount() == null
                                ? null : entry.evidence().occurrences())
                        .patternTypicalAmount(entry.evidence().typicalAmount())
                        .patternAmountConsistent(entry.evidence().amountConsistent())
                        .patternCadenceGuess(entry.evidence().cadenceGuess())
                        .build())
                .toList();
        return ResponseEntity.ok(queue);
    }

    private Map<String, MemberTransactionLabel> loadLabelsByCounterpartyKey(UUID memberId) {
        return labelRepository.findByMemberId(memberId).stream()
                .collect(Collectors.toMap(MemberTransactionLabel::getCounterpartyKey, label -> label));
    }

    private TransactionResponse toResponse(Transaction txn, Map<String, MemberTransactionLabel> labelsByCounterpartyKey) {
        String effectiveCategory = txn.getEffectiveCategory();
        MemberTransactionLabel matchedLabel = counterpartyKeyResolver.resolveKey(txn)
                .map(labelsByCounterpartyKey::get)
                .orElse(null);
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
                .manuallyTagged(txn.getUserCategoryOverride() != null)
                .personalLabel(matchedLabel == null ? null : matchedLabel.getLabel())
                .counterpartyType(txn.getCounterpartyType())
                .payeePurpose(matchedLabel == null ? null : matchedLabel.getPurpose())
                .build();
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByAuthSubject(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
