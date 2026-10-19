package com.prospr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.prospr.app.dto.response.ActionableInsightResponse;
import com.prospr.app.entity.Member;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.TransactionRepository;
import com.prospr.app.service.ActionableInsightService;
import com.prospr.app.service.CurrentMonthResolver;

@ExtendWith(MockitoExtension.class)
class ActionableInsightControllerTest {

    @Mock
    private ActionableInsightService actionableInsightService;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private Authentication authentication;

    private ActionableInsightController controller() {
        return new ActionableInsightController(actionableInsightService, memberRepository,
                new CurrentMonthResolver(transactionRepository));
    }

    @Test
    void actionableMapsServiceResultsForTheCallerOnly() {
        Member caller = Member.builder().id(UUID.randomUUID()).email("caller@example.com").build();
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByAuthSubject(caller.getEmail())).thenReturn(Optional.of(caller));
        when(actionableInsightService.buildInsights(any(), any(), any(), any())).thenReturn(List.of(
                new ActionableInsightService.ActionableInsight(
                        ActionableInsightService.TYPE_REDUCE, "Dining", "You spent ₹4,000 on Dining...", new BigDecimal("800"))));

        List<ActionableInsightResponse> result = controller().actionable(authentication).getBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(ActionableInsightService.TYPE_REDUCE);
        assertThat(result.get(0).getLabel()).isEqualTo("Dining");
        assertThat(result.get(0).getMonthlyImpact()).isEqualByComparingTo("800");
    }
}
