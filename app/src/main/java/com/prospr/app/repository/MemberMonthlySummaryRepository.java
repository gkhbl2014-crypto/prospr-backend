package com.prospr.app.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.MemberMonthlySummary;

@Repository
public interface MemberMonthlySummaryRepository extends JpaRepository<MemberMonthlySummary, UUID> {

    Optional<MemberMonthlySummary> findByMemberIdAndYearAndMonthAndCategory(
            UUID memberId, Integer year, Integer month, String category);

    List<MemberMonthlySummary> findByMemberIdAndYearAndMonth(UUID memberId, Integer year, Integer month);

    /** Every stored category total for the member - lets the monthly trend load all months in one query. */
    List<MemberMonthlySummary> findByMemberId(UUID memberId);

    void deleteByMemberId(UUID memberId);
}
