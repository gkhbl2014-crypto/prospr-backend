package com.prospr.app.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.EmergencyFundAllocation;

@Repository
public interface EmergencyFundAllocationRepository extends JpaRepository<EmergencyFundAllocation, UUID> {

    List<EmergencyFundAllocation> findByMemberIdAndIsActiveTrueOrderByCreatedAtDesc(UUID memberId);

    List<EmergencyFundAllocation> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    Optional<EmergencyFundAllocation> findByIdAndMemberId(UUID id, UUID memberId);

    boolean existsByMemberIdAndMaskedAccountNumberAndIsActiveTrue(UUID memberId, String maskedAccountNumber);
}
