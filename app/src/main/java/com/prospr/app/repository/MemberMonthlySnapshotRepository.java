package com.prospr.app.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prospr.app.entity.MemberMonthlySnapshot;

public interface MemberMonthlySnapshotRepository extends JpaRepository<MemberMonthlySnapshot, UUID> {

    Optional<MemberMonthlySnapshot> findByMemberIdAndYearAndMonth(UUID memberId, Integer year, Integer month);

    List<MemberMonthlySnapshot> findByMemberIdOrderByYearDescMonthDesc(UUID memberId);
}
