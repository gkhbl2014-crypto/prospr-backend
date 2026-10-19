package com.prospr.app.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.request.MutualFundHoldingRequest;
import com.prospr.app.dto.response.MutualFundHoldingResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MutualFundHolding;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.exception.SafetyNetConflictException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.MutualFundHoldingRepository;
import com.prospr.app.service.SafetyNetService;

@RestController
@RequestMapping("/api/mutual-funds")
public class MutualFundController {

    private static final String SETU_SOURCE = "SETU";
    private static final String MANUAL_SOURCE = "MANUAL";

    private final MutualFundHoldingRepository mutualFundHoldingRepository;
    private final MemberRepository memberRepository;
    private final SafetyNetService safetyNetService;

    public MutualFundController(MutualFundHoldingRepository mutualFundHoldingRepository,
                                 MemberRepository memberRepository,
                                 SafetyNetService safetyNetService) {
        this.mutualFundHoldingRepository = mutualFundHoldingRepository;
        this.memberRepository = memberRepository;
        this.safetyNetService = safetyNetService;
    }

    @GetMapping
    public ResponseEntity<List<MutualFundHoldingResponse>> listHoldings(Authentication authentication) {
        Member member = resolveMember(authentication);
        Family family = member.getFamily();

        List<MutualFundHoldingResponse> holdings = (family == null
                ? mutualFundHoldingRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())
                : mutualFundHoldingRepository.findByMemberFamilyIdOrderByCreatedAtDesc(family.getId()))
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(holdings);
    }

    /** MANUAL holdings only - Setu-synced holdings arrive automatically through the AA consent flow. */
    @PostMapping
    public ResponseEntity<MutualFundHoldingResponse> createHolding(@Valid @RequestBody MutualFundHoldingRequest request,
                                                                     Authentication authentication) {
        Member member = resolveMember(authentication);

        MutualFundHolding holding = MutualFundHolding.builder()
                .member(member)
                .source(MANUAL_SOURCE)
                .build();
        applyRequest(holding, request);
        holding = mutualFundHoldingRepository.save(holding);

        safetyNetService.recomputeForMember(member);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(holding));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MutualFundHoldingResponse> updateHolding(@PathVariable UUID id,
                                                                     @Valid @RequestBody MutualFundHoldingRequest request,
                                                                     Authentication authentication) {
        Member member = resolveMember(authentication);
        MutualFundHolding holding = mutualFundHoldingRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Mutual fund holding not found"));
        rejectIfSetuSourced(holding);

        applyRequest(holding, request);
        holding = mutualFundHoldingRepository.save(holding);

        safetyNetService.recomputeForMember(member);
        return ResponseEntity.ok(toResponse(holding));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHolding(@PathVariable UUID id, Authentication authentication) {
        Member member = resolveMember(authentication);
        MutualFundHolding holding = mutualFundHoldingRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Mutual fund holding not found"));
        rejectIfSetuSourced(holding);

        mutualFundHoldingRepository.delete(holding);
        safetyNetService.recomputeForMember(member);
        return ResponseEntity.noContent().build();
    }

    private void rejectIfSetuSourced(MutualFundHolding holding) {
        if (SETU_SOURCE.equals(holding.getSource())) {
            throw new SafetyNetConflictException(
                    "This holding was synced through Account Aggregator and can't be edited manually");
        }
    }

    private void applyRequest(MutualFundHolding holding, MutualFundHoldingRequest request) {
        holding.setAmc(request.getAmc());
        holding.setRegistrar(request.getRegistrar());
        holding.setSchemeCode(request.getSchemeCode());
        holding.setSchemeOption(request.getSchemeOption());
        holding.setIsin(request.getIsin());
        holding.setIsinDescription(request.getIsinDescription());
        holding.setFolioNo(request.getFolioNo());
        holding.setCostValue(request.getCostValue());
        holding.setCurrentValue(request.getCurrentValue());
        holding.setClosingUnits(request.getClosingUnits());
        holding.setNav(request.getNav());
        holding.setNavDate(request.getNavDate());
        holding.setInvestmentDate(request.getInvestmentDate());
    }

    private MutualFundHoldingResponse toResponse(MutualFundHolding holding) {
        return MutualFundHoldingResponse.builder()
                .id(holding.getId())
                .memberId(holding.getMember().getId())
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
                .investmentDate(holding.getInvestmentDate())
                .source(holding.getSource())
                .build();
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
