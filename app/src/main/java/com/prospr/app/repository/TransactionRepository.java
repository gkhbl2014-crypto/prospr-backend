package com.prospr.app.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prospr.app.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    boolean existsBySessionId(String sessionId);

    /** Scoped lookup so a transaction's hidden flag can only ever be toggled by its own owner. */
    Optional<Transaction> findByIdAndMemberId(UUID id, UUID memberId);

    boolean existsByMemberId(UUID memberId);

    List<Transaction> findByMemberIdOrderByTransactionTimestampDesc(UUID memberId);

    /** Combined feed for every member of a household, for the family dashboard. */
    List<Transaction> findByMemberFamilyIdOrderByTransactionTimestampDesc(UUID familyId);

    /** Transactions not yet run through the categorization pipeline. */
    List<Transaction> findByMemberIdAndCategoryIsNull(UUID memberId);

    /** All debit transactions for a member, used to (re)build monthly summaries - filtered to an
     *  effective category in Java (a user override on an otherwise-null category still counts). */
    List<Transaction> findByMemberIdAndTypeIgnoreCase(UUID memberId, String type);

    /** Used to confirm the member's fetched history actually reaches back far enough for a baseline. */
    boolean existsByMemberIdAndValueDateLessThanEqual(UUID memberId, LocalDate date);

    void deleteByMemberId(UUID memberId);
}
