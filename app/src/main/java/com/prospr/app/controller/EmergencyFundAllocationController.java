package com.prospr.app.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.request.EmergencyFundAllocationRequest;
import com.prospr.app.dto.response.EmergencyFundAllocationResponse;
import com.prospr.app.entity.EmergencyFundAllocation;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.Transaction;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.exception.SafetyNetConflictException;
import com.prospr.app.exception.SafetyNetValidationException;
import com.prospr.app.repository.EmergencyFundAllocationRepository;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.TransactionRepository;
import com.prospr.app.service.SafetyNetService;

@RestController
@RequestMapping("/api/emergency-fund/allocations")
public class EmergencyFundAllocationController {

    private static final String ACCOUNT = "ACCOUNT";

    private final EmergencyFundAllocationRepository allocationRepository;
    private final TransactionRepository transactionRepository;
    private final MemberRepository memberRepository;
    private final SafetyNetService safetyNetService;

    public EmergencyFundAllocationController(EmergencyFundAllocationRepository allocationRepository,
                                              TransactionRepository transactionRepository,
                                              MemberRepository memberRepository,
                                              SafetyNetService safetyNetService) {
        this.allocationRepository = allocationRepository;
        this.transactionRepository = transactionRepository;
        this.memberRepository = memberRepository;
        this.safetyNetService = safetyNetService;
    }

    @GetMapping
    public ResponseEntity<List<EmergencyFundAllocationResponse>> list(Authentication authentication) {
        Member member = resolveMember(authentication);
        List<EmergencyFundAllocationResponse> allocations = allocationRepository
                .findByMemberIdOrderByCreatedAtDesc(member.getId())
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(allocations);
    }

    @PostMapping
    public ResponseEntity<EmergencyFundAllocationResponse> create(@Valid @RequestBody EmergencyFundAllocationRequest request,
                                                                    Authentication authentication) {
        Member member = resolveMember(authentication);
        if (ACCOUNT.equals(request.getSourceType())) {
            validateAccountAllocation(member, request.getMaskedAccountNumber(), request.getAllocatedAmount());
            if (allocationRepository.existsByMemberIdAndMaskedAccountNumberAndIsActiveTrue(
                    member.getId(), request.getMaskedAccountNumber())) {
                throw new SafetyNetConflictException(
                        "You already have an active emergency fund allocation for this account");
            }
        }

        EmergencyFundAllocation allocation = EmergencyFundAllocation.builder()
                .member(member)
                .maskedAccountNumber(request.getMaskedAccountNumber())
                .allocatedAmount(request.getAllocatedAmount())
                .isActive(true)
                .sourceType(request.getSourceType())
                .description(request.getDescription())
                .build();
        allocation = allocationRepository.save(allocation);

        safetyNetService.recomputeForMember(member);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(allocation));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmergencyFundAllocationResponse> update(@PathVariable UUID id,
                                                                    @Valid @RequestBody EmergencyFundAllocationRequest request,
                                                                    Authentication authentication) {
        Member member = resolveMember(authentication);
        EmergencyFundAllocation allocation = allocationRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Emergency fund allocation not found"));

        boolean willBeActive = request.getIsActive() != null ? request.getIsActive() : allocation.getIsActive();
        boolean accountChanged = !java.util.Objects.equals(allocation.getMaskedAccountNumber(), request.getMaskedAccountNumber());
        boolean reactivating = willBeActive && !allocation.getIsActive();

        if (ACCOUNT.equals(request.getSourceType())) {
            validateAccountAllocation(member, request.getMaskedAccountNumber(), request.getAllocatedAmount());
            if (willBeActive && (accountChanged || reactivating)
                    && allocationRepository.existsByMemberIdAndMaskedAccountNumberAndIsActiveTrue(
                            member.getId(), request.getMaskedAccountNumber())) {
                throw new SafetyNetConflictException(
                        "You already have an active emergency fund allocation for this account");
            }
        }

        allocation.setMaskedAccountNumber(request.getMaskedAccountNumber());
        allocation.setAllocatedAmount(request.getAllocatedAmount());
        allocation.setSourceType(request.getSourceType());
        allocation.setDescription(request.getDescription());
        if (request.getIsActive() != null) {
            allocation.setIsActive(request.getIsActive());
        }
        allocationRepository.save(allocation);

        safetyNetService.recomputeForMember(member);
        return ResponseEntity.ok(toResponse(allocation));
    }

    /** Deactivates rather than hard-deletes, per spec - the allocation history is kept. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id, Authentication authentication) {
        Member member = resolveMember(authentication);
        EmergencyFundAllocation allocation = allocationRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Emergency fund allocation not found"));

        allocation.setIsActive(false);
        allocationRepository.save(allocation);

        safetyNetService.recomputeForMember(member);
        return ResponseEntity.noContent().build();
    }

    private void validateAccountAllocation(Member member, String maskedAccountNumber, BigDecimal allocatedAmount) {
        if (maskedAccountNumber == null || maskedAccountNumber.isBlank()) {
            throw new SafetyNetValidationException("maskedAccountNumber is required when sourceType is ACCOUNT");
        }

        List<Transaction> memberTransactions = transactionRepository
                .findByMemberIdOrderByTransactionTimestampDesc(member.getId());

        boolean ownsAccount = memberTransactions.stream()
                .anyMatch(t -> maskedAccountNumber.equals(t.getMaskedAccountNumber()));
        if (!ownsAccount) {
            throw new SafetyNetValidationException("This account isn't one of your linked accounts");
        }

        BigDecimal latestBalance = memberTransactions.stream()
                .filter(t -> maskedAccountNumber.equals(t.getMaskedAccountNumber()) && t.getTransactionalBalance() != null)
                .map(Transaction::getTransactionalBalance)
                .findFirst()
                .orElse(null);
        if (latestBalance != null && allocatedAmount.compareTo(latestBalance) > 0) {
            throw new SafetyNetValidationException("Allocated amount exceeds the account's most recent known balance");
        }
    }

    private EmergencyFundAllocationResponse toResponse(EmergencyFundAllocation allocation) {
        return EmergencyFundAllocationResponse.builder()
                .id(allocation.getId())
                .maskedAccountNumber(allocation.getMaskedAccountNumber())
                .allocatedAmount(allocation.getAllocatedAmount())
                .isActive(allocation.getIsActive())
                .sourceType(allocation.getSourceType())
                .description(allocation.getDescription())
                .createdAt(allocation.getCreatedAt())
                .updatedAt(allocation.getUpdatedAt())
                .build();
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
