package com.prospr.app.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.LifestyleBaseline;

@Repository
public interface LifestyleBaselineRepository extends JpaRepository<LifestyleBaseline, UUID> {

    Optional<LifestyleBaseline> findByMemberIdAndCategory(UUID memberId, String category);

    List<LifestyleBaseline> findByMemberId(UUID memberId);

    void deleteByMemberId(UUID memberId);
}
