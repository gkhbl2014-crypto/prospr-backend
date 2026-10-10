package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class CurrentMonthResolverTest {

    @Mock
    private TransactionRepository transactionRepository;

    private CurrentMonthResolver resolver() {
        return new CurrentMonthResolver(transactionRepository);
    }

    @Test
    void fallsBackToTheWallClockMonthWhenTheMemberHasNoTransactionsAtAll() {
        UUID memberId = UUID.randomUUID();
        when(transactionRepository.findFirstByMemberIdOrderByTransactionTimestampDesc(memberId))
                .thenReturn(Optional.empty());

        CurrentMonthResolver.EffectivePeriod period = resolver().resolve(memberId);

        assertThat(period.month()).isEqualTo(YearMonth.now());
        assertThat(period.asOfDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void treatsAPastMonthTheDataStopsAtAsFullyCompletedRatherThanPartial() {
        // Regression: imported data only reaches August while the wall clock has moved on to
        // September - comparing against an empty September would wrongly read as "doing great".
        UUID memberId = UUID.randomUUID();
        LocalDate lastImportedDate = LocalDate.now().minusMonths(1).withDayOfMonth(15);
        Transaction latest = Transaction.builder().valueDate(lastImportedDate)
                .transactionTimestamp(lastImportedDate.atStartOfDay().atOffset(ZoneOffset.UTC)).build();
        when(transactionRepository.findFirstByMemberIdOrderByTransactionTimestampDesc(memberId))
                .thenReturn(Optional.of(latest));

        CurrentMonthResolver.EffectivePeriod period = resolver().resolve(memberId);

        YearMonth expectedMonth = YearMonth.from(lastImportedDate);
        assertThat(period.month()).isEqualTo(expectedMonth);
        assertThat(period.asOfDate()).isEqualTo(expectedMonth.atEndOfMonth());
    }

    @Test
    void treatsTheLiveCalendarMonthAsPartialUsingTheRealTodayForProrating() {
        UUID memberId = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        Transaction latest = Transaction.builder().valueDate(today)
                .transactionTimestamp(OffsetDateTime.now()).build();
        when(transactionRepository.findFirstByMemberIdOrderByTransactionTimestampDesc(memberId))
                .thenReturn(Optional.of(latest));

        CurrentMonthResolver.EffectivePeriod period = resolver().resolve(memberId);

        assertThat(period.month()).isEqualTo(YearMonth.from(today));
        assertThat(period.asOfDate()).isEqualTo(today);
    }

    @Test
    void fallsBackToTheTransactionTimestampWhenValueDateIsMissing() {
        UUID memberId = UUID.randomUUID();
        LocalDate lastImportedDate = LocalDate.now().minusMonths(2).withDayOfMonth(3);
        Transaction latest = Transaction.builder().valueDate(null)
                .transactionTimestamp(lastImportedDate.atStartOfDay().atOffset(ZoneOffset.UTC)).build();
        when(transactionRepository.findFirstByMemberIdOrderByTransactionTimestampDesc(memberId))
                .thenReturn(Optional.of(latest));

        CurrentMonthResolver.EffectivePeriod period = resolver().resolve(memberId);

        assertThat(period.month()).isEqualTo(YearMonth.from(lastImportedDate));
    }
}
