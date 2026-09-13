package com.prospr.app.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.Liability;

@Repository
public interface LiabilityRepository extends JpaRepository<Liability, UUID> {

    List<Liability> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    /** Combined feed for every member of a household, for the family dashboard. */
    List<Liability> findByMemberFamilyIdOrderByCreatedAtDesc(UUID familyId);

    Optional<Liability> findByIdAndMemberId(UUID id, UUID memberId);
}
