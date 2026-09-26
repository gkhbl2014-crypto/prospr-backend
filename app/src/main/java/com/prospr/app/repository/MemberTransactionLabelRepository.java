package com.prospr.app.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.MemberTransactionLabel;

@Repository
public interface MemberTransactionLabelRepository extends JpaRepository<MemberTransactionLabel, UUID> {

    List<MemberTransactionLabel> findByMemberId(UUID memberId);

    Optional<MemberTransactionLabel> findByMemberIdAndCounterpartyKey(UUID memberId, String counterpartyKey);

    void deleteByMemberIdAndCounterpartyKey(UUID memberId, String counterpartyKey);
}
