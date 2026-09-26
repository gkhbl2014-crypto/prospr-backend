package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberMonthlySummary;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.MemberMonthlySummaryRepository;
import com.prospr.app.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class MonthlySpendingSummaryServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private MemberMonthlySummaryRepository summaryRepository;

    private MonthlySpendingSummaryService service() {
        return new MonthlySpendingSummaryService(transactionRepository, summaryRepository);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).build();
    }

    @Test
    void groupsByEffectiveCategorySoAUserOverrideIsReflectedNotTheStaleRawCategory() {
        Member member = member();
        LocalDate today = LocalDate.now();
        Transaction retagged = Transaction.builder().type("DEBIT").category("SHOPPING")
                .userCategoryOverride("INVESTMENT").valueDate(today).amount(new BigDecimal("1000")).build();
        when(transactionRepository.findByMemberIdAndTypeIgnoreCase(member.getId(), "DEBIT"))
                .thenReturn(List.of(retagged));
        when(summaryRepository.findByMemberIdAndYearAndMonthAndCategory(eq(member.getId()), any(), any(), any()))
                .thenReturn(Optional.empty());

        service().recomputeForMember(member);

        ArgumentCaptor<MemberMonthlySummary> captor = ArgumentCaptor.forClass(MemberMonthlySummary.class);
        Mockito.verify(summaryRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("INVESTMENT");
        assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void aTransactionWithNoRawCategoryButAUserOverrideIsStillIncluded() {
        // Previously excluded outright (the DB query required category IS NOT NULL) - an override on
        // an otherwise-uncategorized transaction must not silently disappear from the breakdown.
        Member member = member();
        LocalDate today = LocalDate.now();
        Transaction overridden = Transaction.builder().type("DEBIT").category(null)
                .userCategoryOverride("MARKED_ESSENTIAL").valueDate(today).amount(new BigDecimal("500")).build();
        when(transactionRepository.findByMemberIdAndTypeIgnoreCase(member.getId(), "DEBIT"))
                .thenReturn(List.of(overridden));
        when(summaryRepository.findByMemberIdAndYearAndMonthAndCategory(eq(member.getId()), any(), any(), any()))
                .thenReturn(Optional.empty());

        service().recomputeForMember(member);

        ArgumentCaptor<MemberMonthlySummary> captor = ArgumentCaptor.forClass(MemberMonthlySummary.class);
        Mockito.verify(summaryRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("MARKED_ESSENTIAL");
    }

    @Test
    void transactionsWithNoCategoryAtAllAreExcluded() {
        Member member = member();
        LocalDate today = LocalDate.now();
        Transaction uncategorized = Transaction.builder().type("DEBIT").category(null)
                .valueDate(today).amount(new BigDecimal("500")).build();
        when(transactionRepository.findByMemberIdAndTypeIgnoreCase(member.getId(), "DEBIT"))
                .thenReturn(List.of(uncategorized));

        service().recomputeForMember(member);

        Mockito.verify(summaryRepository, Mockito.never()).save(any());
    }
}
