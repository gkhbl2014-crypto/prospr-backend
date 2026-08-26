package com.prospr.app.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.MemberLifestyleStatus;

@Repository
public interface MemberLifestyleStatusRepository extends JpaRepository<MemberLifestyleStatus, UUID> {

    Optional<MemberLifestyleStatus> findByMemberId(UUID memberId);

    void deleteByMemberId(UUID memberId);
}
