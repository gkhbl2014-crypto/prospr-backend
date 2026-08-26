package com.prospr.app.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prospr.app.entity.Member;
import com.prospr.app.repository.InsuranceRepository;
import com.prospr.app.repository.LifestyleBaselineRepository;
import com.prospr.app.repository.LifestyleInsightRepository;
import com.prospr.app.repository.MemberLifestyleStatusRepository;
import com.prospr.app.repository.MemberMonthlySummaryRepository;
import com.prospr.app.repository.MutualFundHoldingRepository;
import com.prospr.app.repository.TransactionRepository;

/**
 * Wipes everything Setu-derived for one member - used by the "revoke" flow, which deletes local
 * data and then requests a fresh consent, rather than calling any Setu-side revoke API (this
 * integration doesn't expose one). Always self-scoped: callers resolve {@code member} from the
 * caller's own JWT, never from a parameter, so this can never touch another member's data.
 */
@Service
public class MemberDataResetService {

    private static final Logger log = LoggerFactory.getLogger(MemberDataResetService.class);

    private final TransactionRepository transactionRepository;
    private final InsuranceRepository insuranceRepository;
    private final MutualFundHoldingRepository mutualFundHoldingRepository;
    private final MemberMonthlySummaryRepository monthlySummaryRepository;
    private final LifestyleBaselineRepository lifestyleBaselineRepository;
    private final LifestyleInsightRepository lifestyleInsightRepository;
    private final MemberLifestyleStatusRepository lifestyleStatusRepository;

    public MemberDataResetService(TransactionRepository transactionRepository,
                                   InsuranceRepository insuranceRepository,
                                   MutualFundHoldingRepository mutualFundHoldingRepository,
                                   MemberMonthlySummaryRepository monthlySummaryRepository,
                                   LifestyleBaselineRepository lifestyleBaselineRepository,
                                   LifestyleInsightRepository lifestyleInsightRepository,
                                   MemberLifestyleStatusRepository lifestyleStatusRepository) {
        this.transactionRepository = transactionRepository;
        this.insuranceRepository = insuranceRepository;
        this.mutualFundHoldingRepository = mutualFundHoldingRepository;
        this.monthlySummaryRepository = monthlySummaryRepository;
        this.lifestyleBaselineRepository = lifestyleBaselineRepository;
        this.lifestyleInsightRepository = lifestyleInsightRepository;
        this.lifestyleStatusRepository = lifestyleStatusRepository;
    }

    /**
     * Deletes every row tied to this member: transactions, insurance policies, mutual fund
     * holdings, and everything derived from them (monthly summaries, lifestyle baselines/insights,
     * and the lifestyle enrollment status itself - left in place, it would keep showing stale
     * results for data that no longer exists).
     */
    @Transactional
    public void deleteAllFinancialData(Member member) {
        UUID memberId = member.getId();

        lifestyleInsightRepository.deleteByMemberId(memberId);
        lifestyleBaselineRepository.deleteByMemberId(memberId);
        monthlySummaryRepository.deleteByMemberId(memberId);
        lifestyleStatusRepository.deleteByMemberId(memberId);

        transactionRepository.deleteByMemberId(memberId);
        insuranceRepository.deleteByMemberId(memberId);
        mutualFundHoldingRepository.deleteByMemberId(memberId);

        log.info("Deleted all financial and lifestyle data for member '{}'", memberId);
    }
}
