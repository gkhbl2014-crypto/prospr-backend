package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.dto.response.SafetyNetResponse;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.SafetyNetSummary;
import com.prospr.app.repository.SafetyNetSettingsRepository;
import com.prospr.app.repository.SafetyNetSummaryRepository;
import com.prospr.app.service.cache.AnalyticsCacheService;

@ExtendWith(MockitoExtension.class)
class SafetyNetServiceTest {

    @Mock
    private SafetyNetSettingsRepository settingsRepository;
    @Mock
    private SafetyNetSummaryRepository summaryRepository;
    @Mock
    private EssentialExpenseService essentialExpenseService;
    @Mock
    private EmergencyFundService emergencyFundService;
    @Mock
    private HealthCoverageService healthCoverageService;
    @Mock
    private LifeCoverageService lifeCoverageService;
    @Mock
    private AnalyticsCacheService analyticsCacheService;

    private SafetyNetService service() {
        return new SafetyNetService(settingsRepository, summaryRepository, essentialExpenseService,
                emergencyFundService, healthCoverageService, lifeCoverageService, analyticsCacheService);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).build();
    }

    @SuppressWarnings("unchecked")
    private void stubStampedeGuardPassthrough() {
        when(analyticsCacheService.withStampedeGuard(anyString(), any()))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(1)).get());
    }

    private void stubMinimalComputation(Member member) {
        when(settingsRepository.findByMemberId(member.getId())).thenReturn(Optional.empty());
        when(essentialExpenseService.computeAverage(any(), anyInt()))
                .thenReturn(new EssentialExpenseService.Result(BigDecimal.ZERO, 0, 3));
        when(emergencyFundService.compute(any(), any(), any()))
                .thenReturn(new EmergencyFundService.Result(BigDecimal.ZERO, BigDecimal.ZERO, null, 6, null, null, EmergencyFundStatus.UNKNOWN));
        when(healthCoverageService.compute(any()))
                .thenReturn(new HealthCoverageService.Result(BigDecimal.ZERO, BigDecimal.ZERO, 0, HealthCoverageStatus.NO_COVER));
        when(lifeCoverageService.compute(any(), any(), any()))
                .thenReturn(new LifeCoverageService.Result(BigDecimal.ZERO, null, null, LifeCoverageStatus.REVIEW_RECOMMENDED));
    }

    @Test
    void getForMemberReturnsCachedResponseWithoutTouchingPostgres() {
        Member member = member();
        SafetyNetResponse cached = SafetyNetResponse.builder().build();
        when(analyticsCacheService.getSafetyNet(member.getId())).thenReturn(Optional.of(cached));

        SafetyNetResponse result = service().getForMember(member);

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(summaryRepository);
        verifyNoInteractions(settingsRepository);
    }

    @Test
    void getForMemberOnCacheMissComputesAndWritesBack() {
        Member member = member();
        when(analyticsCacheService.getSafetyNet(member.getId())).thenReturn(Optional.empty());
        stubStampedeGuardPassthrough();
        when(summaryRepository.findByMemberId(member.getId())).thenReturn(Optional.empty());
        stubMinimalComputation(member);

        SafetyNetResponse result = service().getForMember(member);

        assertThat(result).isNotNull();
        verify(analyticsCacheService).putSafetyNet(member.getId(), result);
    }

    @Test
    void recomputeForMemberEvictsTheCacheAfterSaving() {
        Member member = member();
        when(summaryRepository.findByMemberId(member.getId())).thenReturn(Optional.empty());
        stubMinimalComputation(member);

        service().recomputeForMember(member);

        verify(summaryRepository, times(1)).save(any(SafetyNetSummary.class));
        verify(analyticsCacheService, times(1)).evictSafetyNet(member.getId());
    }
}
