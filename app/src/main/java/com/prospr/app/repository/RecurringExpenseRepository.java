package com.prospr.app.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.prospr.app.entity.RecurringExpense;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {

    List<RecurringExpense> findByMemberIdOrderByTypicalAmountDesc(UUID memberId);

    Optional<RecurringExpense> findByMemberIdAndMerchantKeyAndCategory(UUID memberId, String merchantKey, String category);

    List<RecurringExpense> findByMemberIdAndClassificationAndStatus(UUID memberId, String classification, String status);
}
