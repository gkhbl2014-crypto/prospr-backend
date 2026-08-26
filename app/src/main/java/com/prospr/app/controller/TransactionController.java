package com.prospr.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.response.TransactionResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;
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

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> listTransactions(Authentication authentication) {
        Member member = memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
        Family family = member.getFamily();

        List<TransactionResponse> transactions = (family == null
                ? transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())
                : transactionRepository.findByMemberFamilyIdOrderByTransactionTimestampDesc(family.getId()))
                .stream()
                .map(txn -> TransactionResponse.builder()
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
                        .build())
                .toList();

        return ResponseEntity.ok(transactions);
    }
}
