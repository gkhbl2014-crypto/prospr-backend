package com.prospr.app.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.Insurance;

@Repository
public interface InsuranceRepository extends JpaRepository<Insurance, UUID> {

    boolean existsBySessionId(String sessionId);

    boolean existsByMemberId(UUID memberId);

    List<Insurance> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    /** Scoped lookup so a policy can only ever be resolved within the caller's own data. */
    Optional<Insurance> findByIdAndMemberId(UUID id, UUID memberId);

    /** Combined feed for every member of a household, for the family dashboard. */
    List<Insurance> findByMemberFamilyIdOrderByCreatedAtDesc(UUID familyId);

    void deleteByMemberId(UUID memberId);
}
