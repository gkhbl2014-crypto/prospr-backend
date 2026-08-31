package com.prospr.app.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.InsuranceCoveredMember;

@Repository
public interface InsuranceCoveredMemberRepository extends JpaRepository<InsuranceCoveredMember, UUID> {

    List<InsuranceCoveredMember> findByInsuranceId(UUID insuranceId);

    /** Every family-floater policy (across insurers/members) that covers this member. */
    List<InsuranceCoveredMember> findByMemberId(UUID memberId);

    void deleteByInsuranceId(UUID insuranceId);
}
