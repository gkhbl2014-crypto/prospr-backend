package com.prospr.app.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prospr.app.config.JwtUtil;
import com.prospr.app.dto.request.FamilyLoginRequest;
import com.prospr.app.dto.request.LoginRequest;
import com.prospr.app.dto.response.FamilyMemberSummaryResponse;
import com.prospr.app.dto.response.LoginResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.InvalidCredentialsException;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.FamilyRepository;
import com.prospr.app.repository.InsuranceRepository;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.MutualFundHoldingRepository;
import com.prospr.app.repository.TransactionRepository;
import com.prospr.app.service.LoginService;

@Service
public class LoginServiceImpl implements LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginServiceImpl.class);
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";
    private static final String INVALID_FAMILY_LOGIN_MESSAGE = "Invalid member or password";

    private final MemberRepository memberRepository;
    private final FamilyRepository familyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TransactionRepository transactionRepository;
    private final InsuranceRepository insuranceRepository;
    private final MutualFundHoldingRepository mutualFundHoldingRepository;

    public LoginServiceImpl(MemberRepository memberRepository,
                             FamilyRepository familyRepository,
                             PasswordEncoder passwordEncoder,
                             JwtUtil jwtUtil,
                             TransactionRepository transactionRepository,
                             InsuranceRepository insuranceRepository,
                             MutualFundHoldingRepository mutualFundHoldingRepository) {
        this.memberRepository = memberRepository;
        this.familyRepository = familyRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.transactionRepository = transactionRepository;
        this.insuranceRepository = insuranceRepository;
        this.mutualFundHoldingRepository = mutualFundHoldingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for email '{}'", request.getEmail());

        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login rejected: no member found for email '{}'", request.getEmail());
                    return new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
                });

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            log.warn("Login rejected: password mismatch for email '{}'", request.getEmail());
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        requireActive(member);
        log.info("Login successful for memberId={}", member.getId());
        return buildLoginResponse(member, "Login successful");
    }

    /**
     * Public, pre-login lookup for the family-code login screen's member picker. Deliberately
     * returns only names/roles (see {@link FamilyMemberSummaryResponse}) - no password is checked
     * here, this just narrows down "who are you" before the actual password prompt.
     */
    @Override
    @Transactional(readOnly = true)
    public List<FamilyMemberSummaryResponse> listFamilyMembers(String inviteCode) {
        Family family = resolveFamilyByInviteCode(inviteCode);
        return memberRepository.findByFamilyIdOrderByCreatedAtAsc(family.getId()).stream()
                .map(member -> FamilyMemberSummaryResponse.builder()
                        .id(member.getId())
                        .firstName(member.getFirstName())
                        .lastName(member.getLastName())
                        .role(member.getRole())
                        .build())
                .toList();
    }

    /**
     * Alternate login path: family code narrows down which account you're picking (convenience -
     * no need to remember/type your email), but the chosen member's own password is still required,
     * so knowing a shared family code alone can never impersonate a specific member.
     */
    @Override
    @Transactional(readOnly = true)
    public LoginResponse familyLogin(FamilyLoginRequest request) {
        log.info("Family-code login attempt for memberId={}", request.getMemberId());

        Family family = resolveFamilyByInviteCode(request.getInviteCode());

        Member member = memberRepository.findByIdAndFamilyId(request.getMemberId(), family.getId())
                .orElseThrow(() -> {
                    log.warn("Family login rejected: memberId={} does not belong to the given family", request.getMemberId());
                    return new InvalidCredentialsException(INVALID_FAMILY_LOGIN_MESSAGE);
                });

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            log.warn("Family login rejected: password mismatch for memberId={}", member.getId());
            throw new InvalidCredentialsException(INVALID_FAMILY_LOGIN_MESSAGE);
        }

        requireActive(member);
        log.info("Family login successful for memberId={}", member.getId());
        return buildLoginResponse(member, "Login successful");
    }

    private Family resolveFamilyByInviteCode(String inviteCode) {
        return familyRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> {
                    log.warn("No family found for the given invite code");
                    return new ResourceNotFoundException("No family found for this code");
                });
    }

    private void requireActive(Member member) {
        if (!"ACTIVE".equalsIgnoreCase(member.getStatus())) {
            log.warn("Login rejected: member '{}' has status '{}'", member.getEmail(), member.getStatus());
            throw new InvalidCredentialsException("Account is not active");
        }
    }

    private LoginResponse buildLoginResponse(Member member, String message) {
        Family family = member.getFamily();
        String token = jwtUtil.generateToken(member.getEmail(), buildClaims(member, family));
        boolean hasLinkedData = transactionRepository.existsByMemberId(member.getId())
                || insuranceRepository.existsByMemberId(member.getId())
                || mutualFundHoldingRepository.existsByMemberId(member.getId());

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .memberId(member.getId())
                .familyId(family != null ? family.getId() : null)
                .firstName(member.getFirstName())
                .lastName(member.getLastName())
                .email(member.getEmail())
                .role(member.getRole())
                .hasLinkedData(hasLinkedData)
                .message(message)
                .build();
    }

    private Map<String, Object> buildClaims(Member member, Family family) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("memberId", member.getId().toString());
        claims.put("familyId", family != null ? family.getId().toString() : null);
        claims.put("role", member.getRole());
        return claims;
    }
}
