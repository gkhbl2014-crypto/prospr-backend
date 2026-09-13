package com.prospr.app.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.request.UpdateTransactionHiddenRequest;
import com.prospr.app.dto.response.TransactionResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.Transaction;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.TransactionRepository;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionRepository transactionRepository;
    private final MemberRepository memberRepository;

    public TransactionController(TransactionRepository transactionRepository, MemberRepository memberRepository) {
        this.transactionRepository = transactionRepository;
        this.memberRepository = memberRepository;
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

        List<TransactionResponse> transactions = (family == null
                ? transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())
                : transactionRepository.findByMemberFamilyIdOrderByTransactionTimestampDesc(family.getId()))
                .stream()
                .map(this::toResponse)
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

        return ResponseEntity.ok(toResponse(txn));
    }

    private TransactionResponse toResponse(Transaction txn) {
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
                .build();
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
