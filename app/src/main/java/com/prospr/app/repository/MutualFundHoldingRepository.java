package com.prospr.app.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.MutualFundHolding;

@Repository
public interface MutualFundHoldingRepository extends JpaRepository<MutualFundHolding, UUID> {

    boolean existsBySessionId(String sessionId);

    boolean existsByMemberId(UUID memberId);

    List<MutualFundHolding> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    /** Scoped lookup so a holding can only ever be resolved within the caller's own data. */
    Optional<MutualFundHolding> findByIdAndMemberId(UUID id, UUID memberId);

    /** Combined feed for every member of a household, for the family dashboard. */
    List<MutualFundHolding> findByMemberFamilyIdOrderByCreatedAtDesc(UUID familyId);

    void deleteByMemberId(UUID memberId);
}
