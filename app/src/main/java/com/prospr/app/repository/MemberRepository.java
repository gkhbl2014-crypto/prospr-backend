package com.prospr.app.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.prospr.app.entity.Member;

@Repository
public interface MemberRepository extends JpaRepository<Member, UUID> {

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Optional<Member> findByEmail(String email);
}
