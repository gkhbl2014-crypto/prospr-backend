package com.prospr.app.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.LifestyleInsight;

@Repository
public interface LifestyleInsightRepository extends JpaRepository<LifestyleInsight, UUID> {

    Optional<LifestyleInsight> findByMemberIdAndYearAndMonthAndCategoryAndInsightType(
            UUID memberId, Integer year, Integer month, String category, String insightType);

    List<LifestyleInsight> findByMemberIdAndYearAndMonthAndStatus(
            UUID memberId, Integer year, Integer month, String status);

    void deleteByMemberId(UUID memberId);
}
