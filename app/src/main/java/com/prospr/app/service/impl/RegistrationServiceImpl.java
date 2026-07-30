package com.prospr.app.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prospr.app.dto.request.RegisterRequest;
import com.prospr.app.dto.response.RegisterResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.DuplicateEmailException;
import com.prospr.app.exception.DuplicatePhoneException;
import com.prospr.app.exception.RegistrationException;
import com.prospr.app.mapper.RegistrationMapper;
import com.prospr.app.repository.FamilyRepository;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.RegistrationService;
import com.prospr.app.util.InviteCodeGenerator;

@Service
public class RegistrationServiceImpl implements RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationServiceImpl.class);
    private static final int MAX_INVITE_CODE_ATTEMPTS = 10;

    private final FamilyRepository familyRepository;
    private final MemberRepository memberRepository;
    private final RegistrationMapper registrationMapper;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final PasswordEncoder passwordEncoder;

    public RegistrationServiceImpl(FamilyRepository familyRepository,
                                    MemberRepository memberRepository,
                                    RegistrationMapper registrationMapper,
                                    InviteCodeGenerator inviteCodeGenerator,
                                    PasswordEncoder passwordEncoder) {
        this.familyRepository = familyRepository;
        this.memberRepository = memberRepository;
        this.registrationMapper = registrationMapper;
        this.inviteCodeGenerator = inviteCodeGenerator;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        log.info("Registering new family '{}' with admin email '{}'", request.getFamilyName(), request.getEmail());

        validateUniqueness(request);

        try {
            String inviteCode = generateUniqueInviteCode();

            Family family = registrationMapper.toFamilyEntity(request, inviteCode);
            family = familyRepository.save(family);
            log.debug("Created family with id={} and inviteCode={}", family.getId(), inviteCode);

            String encodedPassword = passwordEncoder.encode(request.getPassword());
            Member member = registrationMapper.toMemberEntity(request, family, encodedPassword);
            member = memberRepository.save(member);
            log.debug("Created admin member with id={} for familyId={}", member.getId(), family.getId());

            log.info("Registration successful: familyId={}, memberId={}", family.getId(), member.getId());
            return registrationMapper.toRegisterResponse(family, member);

        } catch (DataIntegrityViolationException ex) {
            log.error("Registration failed due to a data integrity violation", ex);
            throw new RegistrationException("Registration failed due to a conflicting record. Please try again.", ex);
        }
    }

    private void validateUniqueness(RegisterRequest request) {
        if (memberRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration rejected: email '{}' is already registered", request.getEmail());
            throw new DuplicateEmailException("An account with email '" + request.getEmail() + "' already exists");
        }
        if (memberRepository.existsByPhone(request.getPhone())) {
            log.warn("Registration rejected: phone '{}' is already registered", request.getPhone());
            throw new DuplicatePhoneException("An account with phone '" + request.getPhone() + "' already exists");
        }
    }

    private String generateUniqueInviteCode() {
        String inviteCode;
        int attempts = 0;
        do {
            if (attempts >= MAX_INVITE_CODE_ATTEMPTS) {
                log.error("Failed to generate a unique invite code after {} attempts", MAX_INVITE_CODE_ATTEMPTS);
                throw new RegistrationException("Unable to generate a unique invite code. Please try again.");
            }
            inviteCode = inviteCodeGenerator.generate();
            attempts++;
        } while (familyRepository.existsByInviteCode(inviteCode));
        return inviteCode;
    }
}
