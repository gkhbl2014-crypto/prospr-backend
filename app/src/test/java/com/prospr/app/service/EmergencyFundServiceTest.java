package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.entity.EmergencyFundAllocation;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.SafetyNetSettings;
import com.prospr.app.repository.EmergencyFundAllocationRepository;

@ExtendWith(MockitoExtension.class)
class EmergencyFundServiceTest {

    @Mock
    private EmergencyFundAllocationRepository allocationRepository;

    private EmergencyFundService service() {
        return new EmergencyFundService(allocationRepository);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).build();
    }

    private SafetyNetSettings settings(int targetMonths) {
        return SafetyNetSettings.builder().emergencyFundTargetMonths(targetMonths).essentialExpenseLookbackMonths(3).build();
    }

    private EmergencyFundAllocation allocation(String amount) {
        return EmergencyFundAllocation.builder().allocatedAmount(new BigDecimal(amount)).isActive(true).build();
    }

    @Test
    void missingEssentialExpenseDataReturnsUnknownStatus() {
        Member member = member();
        when(allocationRepository.findByMemberIdAndIsActiveTrueOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(allocation("50000")));
        EssentialExpenseService.Result essential = new EssentialExpenseService.Result(null, 0, 3);

        EmergencyFundService.Result result = service().compute(member, settings(6), essential);

        assertThat(result.status()).isEqualTo(EmergencyFundStatus.UNKNOWN);
        assertThat(result.coverageMonths()).isNull();
        assertThat(result.targetAmount()).isNull();
        assertThat(result.gap()).isNull();
    }

    @Test
    void noActiveAllocationsReturnsNoEmergencyFundStatus() {
        Member member = member();
        when(allocationRepository.findByMemberIdAndIsActiveTrueOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of());
        EssentialExpenseService.Result essential = new EssentialExpenseService.Result(new BigDecimal("60000.00"), 3, 3);

        EmergencyFundService.Result result = service().compute(member, settings(6), essential);

        assertThat(result.status()).isEqualTo(EmergencyFundStatus.NO_EMERGENCY_FUND);
        assertThat(result.currentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.targetAmount()).isEqualByComparingTo("360000.00");
        assertThat(result.gap()).isEqualByComparingTo("360000.00");
    }

    @Test
    void onlyActiveAllocationsAreSummedAndPartialProgressIsBuildingStatus() {
        Member member = member();
        when(allocationRepository.findByMemberIdAndIsActiveTrueOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(allocation("100000"), allocation("50000")));
        EssentialExpenseService.Result essential = new EssentialExpenseService.Result(new BigDecimal("60000.00"), 3, 3);

        EmergencyFundService.Result result = service().compute(member, settings(6), essential);

        assertThat(result.currentAmount()).isEqualByComparingTo("150000");
        assertThat(result.status()).isEqualTo(EmergencyFundStatus.BUILDING);
    }

    @Test
    void underOneMonthOfCoverageReturnsCriticalStatus() {
        Member member = member();
        when(allocationRepository.findByMemberIdAndIsActiveTrueOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(allocation("30000")));
        EssentialExpenseService.Result essential = new EssentialExpenseService.Result(new BigDecimal("60000.00"), 3, 3);

        EmergencyFundService.Result result = service().compute(member, settings(6), essential);

        assertThat(result.status()).isEqualTo(EmergencyFundStatus.CRITICAL);
        assertThat(result.coverageMonths()).isEqualByComparingTo("0.50");
    }

    @Test
    void targetMetReturnsAdequateStatusAndZeroGap() {
        Member member = member();
        when(allocationRepository.findByMemberIdAndIsActiveTrueOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(allocation("360000")));
        EssentialExpenseService.Result essential = new EssentialExpenseService.Result(new BigDecimal("60000.00"), 3, 3);

        EmergencyFundService.Result result = service().compute(member, settings(6), essential);

        assertThat(result.status()).isEqualTo(EmergencyFundStatus.ADEQUATE);
        assertThat(result.gap()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void zeroEssentialExpenseWithFundsReturnsAdequateAndUndefinedCoverageMonths() {
        Member member = member();
        when(allocationRepository.findByMemberIdAndIsActiveTrueOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(allocation("10000")));
        EssentialExpenseService.Result essential = new EssentialExpenseService.Result(BigDecimal.ZERO, 3, 3);

        EmergencyFundService.Result result = service().compute(member, settings(6), essential);

        assertThat(result.status()).isEqualTo(EmergencyFundStatus.ADEQUATE);
        assertThat(result.coverageMonths()).isNull();
        assertThat(result.gap()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
