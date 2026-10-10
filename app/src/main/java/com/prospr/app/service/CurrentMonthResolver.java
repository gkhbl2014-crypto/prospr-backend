package com.prospr.app.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.TransactionRepository;

/**
 * Resolves the calendar month analytics should treat as "the current month" for a member - the month
 * of their most recent transaction, NOT wall-clock "today". This app's transaction history arrives via
 * manual/batch import (a bank statement PDF, a Setu sandbox pull) rather than a live continuous feed,
 * so it's entirely normal for a member's data to stop at, say, August while the calendar has moved on
 * to September. Comparing spending against an empty month that has no data yet would silently read as
 * "doing great" - every Lifestyle Creep call site should ask "what's the latest month I actually have
 * data for", not "what does the wall clock say".
 */
@Service
public class CurrentMonthResolver {

    /** @param asOfDate the date to treat as "today" within {@code month} - the real wall-clock date
     *                   when {@code month} is genuinely the live calendar month (so an in-progress
     *                   month is still correctly prorated), or that month's last day otherwise (so a
     *                   past month the data has already fully caught up to is never mistaken for a
     *                   partial one). */
    public record EffectivePeriod(YearMonth month, LocalDate asOfDate) {
    }

    private final TransactionRepository transactionRepository;

    public CurrentMonthResolver(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /** Falls back to the real wall-clock month/date for a member with no transactions at all. */
    public EffectivePeriod resolve(UUID memberId) {
        LocalDate today = LocalDate.now();
        YearMonth wallClockMonth = YearMonth.from(today);

        Optional<LocalDate> latestTransactionDate = transactionRepository
                .findFirstByMemberIdOrderByTransactionTimestampDesc(memberId)
                .map(this::resolveDate);
        if (latestTransactionDate.isEmpty()) {
            return new EffectivePeriod(wallClockMonth, today);
        }

        YearMonth latestMonth = YearMonth.from(latestTransactionDate.get());
        return latestMonth.equals(wallClockMonth)
                ? new EffectivePeriod(latestMonth, today)
                : new EffectivePeriod(latestMonth, latestMonth.atEndOfMonth());
    }

    private LocalDate resolveDate(Transaction txn) {
        if (txn.getValueDate() != null) {
            return txn.getValueDate();
        }
        return txn.getTransactionTimestamp() != null ? txn.getTransactionTimestamp().toLocalDate() : null;
    }
}
