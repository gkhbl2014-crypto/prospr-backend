package com.prospr.app.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.SafetyNetSettings;

@Repository
public interface SafetyNetSettingsRepository extends JpaRepository<SafetyNetSettings, UUID> {

    Optional<SafetyNetSettings> findByMemberId(UUID memberId);
}
