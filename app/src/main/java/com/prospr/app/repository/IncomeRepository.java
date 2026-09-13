package com.prospr.app.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.Income;

@Repository
public interface IncomeRepository extends JpaRepository<Income, UUID> {

    List<Income> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    /** Combined feed for every member of a household, for the family dashboard. */
    List<Income> findByMemberFamilyIdOrderByCreatedAtDesc(UUID familyId);

    Optional<Income> findByIdAndMemberId(UUID id, UUID memberId);
}
