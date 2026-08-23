package com.prospr.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.response.MutualFundHoldingResponse;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.MutualFundHoldingRepository;

@RestController
@RequestMapping("/api/mutual-funds")
public class MutualFundController {

    private final MutualFundHoldingRepository mutualFundHoldingRepository;
    private final MemberRepository memberRepository;

    public MutualFundController(MutualFundHoldingRepository mutualFundHoldingRepository, MemberRepository memberRepository) {
        this.mutualFundHoldingRepository = mutualFundHoldingRepository;
        this.memberRepository = memberRepository;
    }

    @GetMapping
    public ResponseEntity<List<MutualFundHoldingResponse>> listHoldings(Authentication authentication) {
        Member member = resolveMember(authentication);

        List<MutualFundHoldingResponse> holdings = mutualFundHoldingRepository
                .findByMemberIdOrderByCreatedAtDesc(member.getId())
                .stream()
                .map(holding -> MutualFundHoldingResponse.builder()
                        .id(holding.getId())
                        .maskedAccountNumber(holding.getMaskedAccountNumber())
                        .costValue(holding.getCostValue())
                        .currentValue(holding.getCurrentValue())
                        .amc(holding.getAmc())
                        .registrar(holding.getRegistrar())
                        .schemeCode(holding.getSchemeCode())
                        .schemeOption(holding.getSchemeOption())
                        .isin(holding.getIsin())
                        .isinDescription(holding.getIsinDescription())
                        .folioNo(holding.getFolioNo())
                        .closingUnits(holding.getClosingUnits())
                        .nav(holding.getNav())
                        .navDate(holding.getNavDate())
                        .build())
                .toList();

        return ResponseEntity.ok(holdings);
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
