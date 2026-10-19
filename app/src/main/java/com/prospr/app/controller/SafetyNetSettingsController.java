package com.prospr.app.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.request.SafetyNetSettingsRequest;
import com.prospr.app.dto.response.SafetyNetSettingsResponse;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.SafetyNetSettings;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.SafetyNetSettingsRepository;
import com.prospr.app.service.SafetyNetService;

@RestController
@RequestMapping("/api/safety-net/settings")
public class SafetyNetSettingsController {

    private static final int DEFAULT_TARGET_MONTHS = 6;
    private static final int DEFAULT_LOOKBACK_MONTHS = 3;

    private final SafetyNetSettingsRepository settingsRepository;
    private final MemberRepository memberRepository;
    private final SafetyNetService safetyNetService;

    public SafetyNetSettingsController(SafetyNetSettingsRepository settingsRepository,
                                        MemberRepository memberRepository,
                                        SafetyNetService safetyNetService) {
        this.settingsRepository = settingsRepository;
        this.memberRepository = memberRepository;
        this.safetyNetService = safetyNetService;
    }

    /** Doesn't create a row on read - returns the documented defaults (6/3, everything else null)
     *  if the member has never saved settings before. */
    @GetMapping
    public ResponseEntity<SafetyNetSettingsResponse> getSettings(Authentication authentication) {
        Member member = resolveMember(authentication);
        SafetyNetSettingsResponse response = settingsRepository.findByMemberId(member.getId())
                .map(this::toResponse)
                .orElseGet(() -> SafetyNetSettingsResponse.builder()
                        .emergencyFundTargetMonths(DEFAULT_TARGET_MONTHS)
                        .essentialExpenseLookbackMonths(DEFAULT_LOOKBACK_MONTHS)
                        .build());
        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<SafetyNetSettingsResponse> updateSettings(@Valid @RequestBody SafetyNetSettingsRequest request,
                                                                      Authentication authentication) {
        Member member = resolveMember(authentication);
        SafetyNetSettings settings = settingsRepository.findByMemberId(member.getId())
                .orElseGet(() -> SafetyNetSettings.builder()
                        .member(member)
                        .emergencyFundTargetMonths(DEFAULT_TARGET_MONTHS)
                        .essentialExpenseLookbackMonths(DEFAULT_LOOKBACK_MONTHS)
                        .build());

        if (request.getEmergencyFundTargetMonths() != null) {
            settings.setEmergencyFundTargetMonths(request.getEmergencyFundTargetMonths());
        }
        if (request.getEssentialExpenseLookbackMonths() != null) {
            settings.setEssentialExpenseLookbackMonths(request.getEssentialExpenseLookbackMonths());
        }
        settings.setIncomeReplacementYears(request.getIncomeReplacementYears());
        settings.setFutureFinancialGoalsAmount(request.getFutureFinancialGoalsAmount());
        settings.setAssetsAvailableForDependents(request.getAssetsAvailableForDependents());
        settings.setOutstandingLiabilitiesAmount(request.getOutstandingLiabilitiesAmount());

        settings = settingsRepository.save(settings);
        safetyNetService.recomputeForMember(member);
        return ResponseEntity.ok(toResponse(settings));
    }

    private SafetyNetSettingsResponse toResponse(SafetyNetSettings settings) {
        return SafetyNetSettingsResponse.builder()
                .id(settings.getId())
                .emergencyFundTargetMonths(settings.getEmergencyFundTargetMonths())
                .essentialExpenseLookbackMonths(settings.getEssentialExpenseLookbackMonths())
                .incomeReplacementYears(settings.getIncomeReplacementYears())
                .futureFinancialGoalsAmount(settings.getFutureFinancialGoalsAmount())
                .assetsAvailableForDependents(settings.getAssetsAvailableForDependents())
                .outstandingLiabilitiesAmount(settings.getOutstandingLiabilitiesAmount())
                .build();
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
