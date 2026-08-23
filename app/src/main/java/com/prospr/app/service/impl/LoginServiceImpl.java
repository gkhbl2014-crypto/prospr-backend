package com.prospr.app.service.impl;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prospr.app.config.JwtUtil;
import com.prospr.app.dto.request.LoginRequest;
import com.prospr.app.dto.response.LoginResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.InvalidCredentialsException;
import com.prospr.app.repository.InsuranceRepository;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.MutualFundHoldingRepository;
import com.prospr.app.repository.TransactionRepository;
import com.prospr.app.service.LoginService;

@Service
public class LoginServiceImpl implements LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginServiceImpl.class);
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TransactionRepository transactionRepository;
    private final InsuranceRepository insuranceRepository;
    private final MutualFundHoldingRepository mutualFundHoldingRepository;

    public LoginServiceImpl(MemberRepository memberRepository,
                             PasswordEncoder passwordEncoder,
                             JwtUtil jwtUtil,
                             TransactionRepository transactionRepository,
                             InsuranceRepository insuranceRepository,
                             MutualFundHoldingRepository mutualFundHoldingRepository) {
        this.memberRepository = memberRepository;
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

        if (!"ACTIVE".equalsIgnoreCase(member.getStatus())) {
            log.warn("Login rejected: member '{}' has status '{}'", request.getEmail(), member.getStatus());
            throw new InvalidCredentialsException("Account is not active");
        }

        Family family = member.getFamily();
        String token = jwtUtil.generateToken(member.getEmail(), buildClaims(member, family));
        boolean hasLinkedData = transactionRepository.existsByMemberId(member.getId())
                || insuranceRepository.existsByMemberId(member.getId())
                || mutualFundHoldingRepository.existsByMemberId(member.getId());

        log.info("Login successful for memberId={}", member.getId());

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
                .message("Login successful")
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
