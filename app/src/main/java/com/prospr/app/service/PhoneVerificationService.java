package com.prospr.app.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prospr.app.config.OtpProperties;
import com.prospr.app.entity.PhoneVerification;
import com.prospr.app.exception.OtpException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.PhoneVerificationRepository;
import com.prospr.app.util.PhoneNumbers;

/**
 * Proves a person controls a mobile number before an account is created for it: send a 6-digit code,
 * verify it (hashed at rest, attempt-capped, expiring), then hand back the verification's id, which
 * registration must present and which can be used exactly once.
 */
@Service
public class PhoneVerificationService {

    private final PhoneVerificationRepository repository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsGateway smsGateway;
    private final OtpProperties properties;
    private final SecureRandom random = new SecureRandom();

    public PhoneVerificationService(PhoneVerificationRepository repository,
                                     MemberRepository memberRepository,
                                     PasswordEncoder passwordEncoder,
                                     SmsGateway smsGateway,
                                     OtpProperties properties) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsGateway = smsGateway;
        this.properties = properties;
    }

    /** Rolls back if the SMS can't be sent, so a failed send never counts against the rate limit. */
    @Transactional
    public void sendOtp(String rawPhone) {
        String phone = normalize(rawPhone);
        if (memberRepository.existsByPhone(phone)) {
            throw new OtpException("An account with this mobile number already exists", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        repository.findFirstByPhoneOrderByCreatedAtDesc(phone).ifPresent(latest -> {
            long waitSeconds = java.time.Duration.between(now,
                    latest.getCreatedAt().plusSeconds(properties.getResendCooldownSeconds())).getSeconds() + 1;
            if (waitSeconds > 0) {
                throw new OtpException("Please wait " + waitSeconds + " seconds before requesting another code",
                        HttpStatus.TOO_MANY_REQUESTS);
            }
        });
        if (repository.countByPhoneAndCreatedAtAfter(phone, now.minusHours(1)) >= properties.getMaxSendsPerHour()) {
            throw new OtpException("Too many codes requested for this number. Try again in an hour.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        repository.save(PhoneVerification.builder()
                .phone(phone)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(now.plusMinutes(properties.getExpiryMinutes()))
                .attempts(0)
                .build());
        smsGateway.sendOtp(phone, code);
    }

    /** Wrong guesses must be persisted, so a failed verification doesn't roll back its attempt count. */
    @Transactional(noRollbackFor = OtpException.class)
    public UUID verifyOtp(String rawPhone, String code) {
        String phone = normalize(rawPhone);
        LocalDateTime now = LocalDateTime.now();

        PhoneVerification verification = repository.findFirstByPhoneOrderByCreatedAtDesc(phone)
                .orElseThrow(() -> new OtpException("Request a code first", HttpStatus.BAD_REQUEST));

        if (verification.getConsumedAt() != null) {
            throw new OtpException("This code was already used. Request a new one.", HttpStatus.BAD_REQUEST);
        }
        if (verification.getVerifiedAt() != null) {
            return verification.getId();
        }
        if (now.isAfter(verification.getExpiresAt())) {
            throw new OtpException("This code has expired. Request a new one.", HttpStatus.BAD_REQUEST);
        }
        if (verification.getAttempts() >= properties.getMaxVerifyAttempts()) {
            throw new OtpException("Too many incorrect attempts. Request a new code.", HttpStatus.TOO_MANY_REQUESTS);
        }

        if (code == null || !passwordEncoder.matches(code.trim(), verification.getCodeHash())) {
            verification.setAttempts(verification.getAttempts() + 1);
            repository.save(verification);
            int remaining = properties.getMaxVerifyAttempts() - verification.getAttempts();
            throw new OtpException(remaining > 0
                    ? "Incorrect code. " + remaining + (remaining == 1 ? " attempt" : " attempts") + " left."
                    : "Too many incorrect attempts. Request a new code.",
                    remaining > 0 ? HttpStatus.BAD_REQUEST : HttpStatus.TOO_MANY_REQUESTS);
        }

        verification.setVerifiedAt(now);
        repository.save(verification);
        return verification.getId();
    }

    /** Called by registration. Single-use, and only valid for a limited time after verifying. */
    @Transactional
    public void consumeVerification(String phone, UUID verificationId) {
        LocalDateTime now = LocalDateTime.now();
        PhoneVerification verification = (verificationId == null ? java.util.Optional.<PhoneVerification>empty()
                : repository.findByIdAndPhone(verificationId, phone))
                .orElseThrow(() -> new OtpException("Verify your mobile number to continue", HttpStatus.BAD_REQUEST));

        boolean usable = verification.getVerifiedAt() != null
                && verification.getConsumedAt() == null
                && now.isBefore(verification.getVerifiedAt().plusMinutes(properties.getVerifiedValidMinutes()));
        if (!usable) {
            throw new OtpException("Your mobile verification has expired. Please verify again.", HttpStatus.BAD_REQUEST);
        }
        verification.setConsumedAt(now);
        repository.save(verification);
    }

    private String normalize(String rawPhone) {
        try {
            return PhoneNumbers.normalizeIndian(rawPhone);
        } catch (IllegalArgumentException ex) {
            throw new OtpException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
