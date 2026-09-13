package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.config.LifestyleProperties;
import com.prospr.app.entity.LifestyleBaseline;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberMonthlySummary;
import com.prospr.app.repository.LifestyleBaselineRepository;
import com.prospr.app.repository.MemberMonthlySummaryRepository;
import com.prospr.app.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class LifestyleBaselineServiceTest {

    @Mock
    private MemberMonthlySummaryRepository summaryRepository;
    @Mock
    private LifestyleBaselineRepository baselineRepository;
    @Mock
    private TransactionRepository transactionRepository;

    private final LifestyleCategoryCatalog catalog = new LifestyleCategoryCatalog();
    private final LifestyleProperties properties = new LifestyleProperties();

    private LifestyleBaselineService service() {
        return new LifestyleBaselineService(summaryRepository, baselineRepository, transactionRepository, catalog, properties);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).build();
    }

    @Test
    void fewerThanThreeCompletedMonthsReturnsInsufficientHistory() {
        Member member = member();
        when(transactionRepository.existsByMemberIdAndValueDateLessThanEqual(eq(member.getId()), any()))
                .thenReturn(false);

        LifestyleBaselineService.Result result = service().recomputeForMember(member, YearMonth.of(2026, 8));

        assertThat(result).isEqualTo(LifestyleBaselineService.Result.INSUFFICIENT_HISTORY);
        verify(baselineRepository, never()).save(any());
    }

    @Test
    void averagesThreeCompletedMonthsTreatingAMissingCategoryRowAsZeroSpend() {
        properties.setBaselineMonths(3);
        Member member = member();
        when(transactionRepository.existsByMemberIdAndValueDateLessThanEqual(eq(member.getId()), any()))
                .thenReturn(true);

        // Dining: May=4000, June=4500, July=5000 -> average 4500 (matches the worked example).
        when(summaryRepository.findByMemberIdAndYearAndMonthAndCategory(member.getId(), 2026, 5, "DINING"))
                .thenReturn(Optional.of(summary(new BigDecimal("4000"))));
        when(summaryRepository.findByMemberIdAndYearAndMonthAndCategory(member.getId(), 2026, 6, "DINING"))
                .thenReturn(Optional.of(summary(new BigDecimal("4500"))));
        when(summaryRepository.findByMemberIdAndYearAndMonthAndCategory(member.getId(), 2026, 7, "DINING"))
                .thenReturn(Optional.of(summary(new BigDecimal("5000"))));
        // Every other category/month combination has no row -> treated as zero (coverage confirmed).
        when(summaryRepository.findByMemberIdAndYearAndMonthAndCategory(eq(member.getId()), any(), any(),
                org.mockito.ArgumentMatchers.argThat(cat -> !"DINING".equals(cat))))
                .thenReturn(Optional.empty());

        when(baselineRepository.findByMemberIdAndCategory(any(), any())).thenReturn(Optional.empty());

        LifestyleBaselineService.Result result = service().recomputeForMember(member, YearMonth.of(2026, 8));

        assertThat(result).isEqualTo(LifestyleBaselineService.Result.COMPUTED);

        org.mockito.ArgumentCaptor<LifestyleBaseline> captor = org.mockito.ArgumentCaptor.forClass(LifestyleBaseline.class);
        verify(baselineRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());

        LifestyleBaseline diningBaseline = captor.getAllValues().stream()
                .filter(b -> "DINING".equals(b.getCategory()))
                .findFirst()
                .orElseThrow();
        assertThat(diningBaseline.getBaselineAmount()).isEqualByComparingTo("4500.00");
        assertThat(diningBaseline.getMinimumAmount()).isEqualByComparingTo("4000");
        assertThat(diningBaseline.getMaximumAmount()).isEqualByComparingTo("5000");
    }

    @Test
    void usesConfiguredWindowSizeRatherThanHardcodedThreeMonths() {
        // Default LifestyleProperties.baselineMonths is 6 - the whole point of this test is that the
        // service actually reads that value instead of always building a fixed 3-month window.
        assertThat(properties.getBaselineMonths()).isEqualTo(6);
        Member member = member();
        when(transactionRepository.existsByMemberIdAndValueDateLessThanEqual(eq(member.getId()), any()))
                .thenReturn(true);
        when(summaryRepository.findByMemberIdAndYearAndMonthAndCategory(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(baselineRepository.findByMemberIdAndCategory(any(), any())).thenReturn(Optional.empty());

        service().recomputeForMember(member, YearMonth.of(2026, 8));

        // Window should be Feb..Jul 2026 (6 months strictly before August) - confirm the coverage
        // check reaches back to the start of February, not May (the old hardcoded 3-month start).
        verify(transactionRepository)
                .existsByMemberIdAndValueDateLessThanEqual(member.getId(), java.time.LocalDate.of(2026, 2, 1));
        verify(summaryRepository, org.mockito.Mockito.atLeastOnce())
                .findByMemberIdAndYearAndMonthAndCategory(eq(member.getId()), eq(2026), eq(2), any());
        verify(summaryRepository, org.mockito.Mockito.atLeastOnce())
                .findByMemberIdAndYearAndMonthAndCategory(eq(member.getId()), eq(2026), eq(7), any());
    }

    private MemberMonthlySummary summary(BigDecimal total) {
        return MemberMonthlySummary.builder().totalAmount(total).build();
    }
}
