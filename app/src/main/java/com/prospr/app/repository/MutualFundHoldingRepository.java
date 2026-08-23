package com.prospr.app.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.MutualFundHolding;

@Repository
public interface MutualFundHoldingRepository extends JpaRepository<MutualFundHolding, UUID> {

    boolean existsBySessionId(String sessionId);

    boolean existsByMemberId(UUID memberId);

    List<MutualFundHolding> findByMemberIdOrderByCreatedAtDesc(UUID memberId);
}
