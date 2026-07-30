package com.prospr.app.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.Family;

@Repository
public interface FamilyRepository extends JpaRepository<Family, UUID> {

    boolean existsByInviteCode(String inviteCode);
}
