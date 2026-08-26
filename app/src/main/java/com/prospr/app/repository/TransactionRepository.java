package com.prospr.app.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prospr.app.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    boolean existsBySessionId(String sessionId);

    boolean existsByMemberId(UUID memberId);

    List<Transaction> findByMemberIdOrderByTransactionTimestampDesc(UUID memberId);

    /** Combined feed for every member of a household, for the family dashboard. */
    List<Transaction> findByMemberFamilyIdOrderByTransactionTimestampDesc(UUID familyId);

    /** Transactions not yet run through the categorization pipeline. */
    List<Transaction> findByMemberIdAndCategoryIsNull(UUID memberId);

    /** All categorized debit transactions for a member, used to (re)build monthly summaries. */
    List<Transaction> findByMemberIdAndCategoryIsNotNullAndTypeIgnoreCase(UUID memberId, String type);

    /** Used to confirm the member's fetched history actually reaches back far enough for a baseline. */
    boolean existsByMemberIdAndValueDateLessThanEqual(UUID memberId, LocalDate date);

    void deleteByMemberId(UUID memberId);
}
