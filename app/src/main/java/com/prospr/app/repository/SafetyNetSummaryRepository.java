package com.prospr.app.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.SafetyNetSummary;

@Repository
public interface SafetyNetSummaryRepository extends JpaRepository<SafetyNetSummary, UUID> {

    Optional<SafetyNetSummary> findByMemberId(UUID memberId);
}
