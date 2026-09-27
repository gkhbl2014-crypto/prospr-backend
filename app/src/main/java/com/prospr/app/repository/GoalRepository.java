package com.prospr.app.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.Goal;

@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByMemberIdOrderByCreatedAtDesc(UUID memberId);

    Optional<Goal> findByIdAndMemberId(UUID id, UUID memberId);
}
