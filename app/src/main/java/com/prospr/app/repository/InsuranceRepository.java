package com.prospr.app.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.Insurance;

@Repository
public interface InsuranceRepository extends JpaRepository<Insurance, UUID> {

    boolean existsBySessionId(String sessionId);

    boolean existsByMemberId(UUID memberId);

    List<Insurance> findByMemberIdOrderByCreatedAtDesc(UUID memberId);
}
