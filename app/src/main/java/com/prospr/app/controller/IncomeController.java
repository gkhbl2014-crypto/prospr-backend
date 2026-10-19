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

import com.prospr.app.dto.request.IncomeRequest;
import com.prospr.app.dto.response.IncomeResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Income;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.exception.SafetyNetConflictException;
import com.prospr.app.repository.IncomeRepository;
import com.prospr.app.repository.MemberRepository;

@RestController
@RequestMapping("/api/income")
public class IncomeController {

    private static final String SETU_SOURCE = "SETU";
    private static final String MANUAL_SOURCE = "MANUAL";

    private final IncomeRepository incomeRepository;
    private final MemberRepository memberRepository;

    public IncomeController(IncomeRepository incomeRepository, MemberRepository memberRepository) {
        this.incomeRepository = incomeRepository;
        this.memberRepository = memberRepository;
    }

    @GetMapping
    public ResponseEntity<List<IncomeResponse>> listIncome(Authentication authentication) {
        Member member = resolveMember(authentication);
        Family family = member.getFamily();

        List<IncomeResponse> income = (family == null
                ? incomeRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())
                : incomeRepository.findByMemberFamilyIdOrderByCreatedAtDesc(family.getId()))
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(income);
    }

    @PostMapping
    public ResponseEntity<IncomeResponse> createIncome(@Valid @RequestBody IncomeRequest request,
                                                         Authentication authentication) {
        Member member = resolveMember(authentication);

        Income income = Income.builder()
                .member(member)
                .source(MANUAL_SOURCE)
                .build();
        applyRequest(income, request);
        income = incomeRepository.save(income);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(income));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IncomeResponse> updateIncome(@PathVariable UUID id,
                                                         @Valid @RequestBody IncomeRequest request,
                                                         Authentication authentication) {
        Member member = resolveMember(authentication);
        Income income = incomeRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Income record not found"));
        rejectIfSetuSourced(income);

        applyRequest(income, request);
        income = incomeRepository.save(income);

        return ResponseEntity.ok(toResponse(income));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIncome(@PathVariable UUID id, Authentication authentication) {
        Member member = resolveMember(authentication);
        Income income = incomeRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Income record not found"));
        rejectIfSetuSourced(income);

        incomeRepository.delete(income);
        return ResponseEntity.noContent().build();
    }

    private void rejectIfSetuSourced(Income income) {
        if (SETU_SOURCE.equals(income.getSource())) {
            throw new SafetyNetConflictException(
                    "This income record was linked through Account Aggregator and can't be edited manually");
        }
    }

    private void applyRequest(Income income, IncomeRequest request) {
        income.setIncomeType(request.getIncomeType());
        income.setMonthlyAmount(request.getMonthlyAmount());
        income.setFrequency(request.getFrequency());
    }

    private IncomeResponse toResponse(Income income) {
        return IncomeResponse.builder()
                .id(income.getId())
                .memberId(income.getMember().getId())
                .incomeType(income.getIncomeType())
                .monthlyAmount(income.getMonthlyAmount())
                .frequency(income.getFrequency())
                .source(income.getSource())
                .build();
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
