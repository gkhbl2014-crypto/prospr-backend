package com.prospr.app.repository;

import java.util.List;
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

    Optional<Member> findByPhone(String phone);

    /** Resolves the logged-in member from the JWT subject, which is the member's id (not email -
     *  email is optional, so it can't be the identity). A non-UUID subject, e.g. a token issued
     *  before this change, resolves to empty rather than throwing. */
    default Optional<Member> findByAuthSubject(String subject) {
        try {
            return findById(UUID.fromString(subject));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** For the family-code login screen's member picker. */
    List<Member> findByFamilyIdOrderByCreatedAtAsc(UUID familyId);

    /** Scoped lookup so a memberId can only ever be resolved within the family it claims. */
    Optional<Member> findByIdAndFamilyId(UUID id, UUID familyId);
}
