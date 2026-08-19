package com.prospr.app.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prospr.app.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    boolean existsBySessionId(String sessionId);

    List<Transaction> findByMemberIdOrderByTransactionTimestampDesc(UUID memberId);
}
