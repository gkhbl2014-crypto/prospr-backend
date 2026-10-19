package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.prospr.app.config.OtpProperties;
import com.prospr.app.entity.PhoneVerification;
import com.prospr.app.exception.OtpException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.PhoneVerificationRepository;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

    private static final String PHONE = "+919810012345";
    private static final String CODE = "123456";

    @Mock
    private PhoneVerificationRepository repository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private SmsGateway smsGateway;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final OtpProperties properties = new OtpProperties();

    private PhoneVerificationService service() {
        return new PhoneVerificationService(repository, memberRepository, passwordEncoder, smsGateway, properties);
    }

    private PhoneVerification challenge(int attempts, LocalDateTime expiresAt) {
        return PhoneVerification.builder().id(UUID.randomUUID()).phone(PHONE)
                .codeHash(passwordEncoder.encode(CODE)).expiresAt(expiresAt).attempts(attempts)
                .createdAt(LocalDateTime.now().minusMinutes(1)).build();
    }

    @Test
    void sendStoresOnlyAHashOfTheCodeAndTextsTheCodeItself() {
        when(memberRepository.existsByPhone(PHONE)).thenReturn(false);
        when(repository.findFirstByPhoneOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.empty());

        service().sendOtp("98100 12345");

        ArgumentCaptor<PhoneVerification> saved = ArgumentCaptor.forClass(PhoneVerification.class);
        verify(repository).save(saved.capture());
        ArgumentCaptor<String> sentCode = ArgumentCaptor.forClass(String.class);
        verify(smsGateway).sendOtp(org.mockito.ArgumentMatchers.eq(PHONE), sentCode.capture());
        assertThat(sentCode.getValue()).matches("\\d{6}");
        assertThat(saved.getValue().getCodeHash()).isNotEqualTo(sentCode.getValue());
        assertThat(passwordEncoder.matches(sentCode.getValue(), saved.getValue().getCodeHash())).isTrue();
    }

    @Test
    void sendRefusesANumberThatAlreadyHasAnAccount() {
        when(memberRepository.existsByPhone(PHONE)).thenReturn(true);

        assertThatThrownBy(() -> service().sendOtp(PHONE))
                .isInstanceOfSatisfying(OtpException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(smsGateway, never()).sendOtp(anyString(), anyString());
    }

    @Test
    void sendEnforcesTheResendCooldown() {
        when(memberRepository.existsByPhone(PHONE)).thenReturn(false);
        PhoneVerification justSent = challenge(0, LocalDateTime.now().plusMinutes(9));
        justSent.setCreatedAt(LocalDateTime.now().minusSeconds(5));
        when(repository.findFirstByPhoneOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(justSent));

        assertThatThrownBy(() -> service().sendOtp(PHONE))
                .isInstanceOfSatisfying(OtpException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(smsGateway, never()).sendOtp(anyString(), anyString());
    }

    @Test
    void sendEnforcesTheHourlyLimit() {
        when(memberRepository.existsByPhone(PHONE)).thenReturn(false);
        when(repository.findFirstByPhoneOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.empty());
        when(repository.countByPhoneAndCreatedAtAfter(org.mockito.ArgumentMatchers.eq(PHONE), any())).thenReturn(5L);

        assertThatThrownBy(() -> service().sendOtp(PHONE))
                .isInstanceOfSatisfying(OtpException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        verify(smsGateway, never()).sendOtp(anyString(), anyString());
    }

    @Test
    void verifyWithTheRightCodeReturnsTheVerificationId() {
        PhoneVerification pending = challenge(0, LocalDateTime.now().plusMinutes(5));
        when(repository.findFirstByPhoneOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(pending));

        UUID id = service().verifyOtp("9810012345", CODE);

        assertThat(id).isEqualTo(pending.getId());
        assertThat(pending.getVerifiedAt()).isNotNull();
    }

    @Test
    void aWrongCodeCountsAnAttemptAndEventuallyLocksTheChallenge() {
        PhoneVerification pending = challenge(3, LocalDateTime.now().plusMinutes(5));
        when(repository.findFirstByPhoneOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service().verifyOtp(PHONE, "000000")).isInstanceOf(OtpException.class);
        assertThat(pending.getAttempts()).isEqualTo(4);
        assertThatThrownBy(() -> service().verifyOtp(PHONE, "000000")).isInstanceOf(OtpException.class);
        assertThat(pending.getAttempts()).isEqualTo(5);

        // Even the correct code is refused once the attempts are used up.
        assertThatThrownBy(() -> service().verifyOtp(PHONE, CODE))
                .isInstanceOfSatisfying(OtpException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(pending.getVerifiedAt()).isNull();
    }

    @Test
    void verifyRejectsAnExpiredCode() {
        PhoneVerification expired = challenge(0, LocalDateTime.now().minusMinutes(1));
        when(repository.findFirstByPhoneOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service().verifyOtp(PHONE, CODE)).isInstanceOf(OtpException.class);
        assertThat(expired.getVerifiedAt()).isNull();
    }

    @Test
    void aVerificationCanBeConsumedExactlyOnce() {
        PhoneVerification verified = challenge(0, LocalDateTime.now().plusMinutes(5));
        verified.setVerifiedAt(LocalDateTime.now().minusMinutes(1));
        when(repository.findByIdAndPhone(verified.getId(), PHONE)).thenReturn(Optional.of(verified));

        service().consumeVerification(PHONE, verified.getId());
        assertThat(verified.getConsumedAt()).isNotNull();

        assertThatThrownBy(() -> service().consumeVerification(PHONE, verified.getId()))
                .isInstanceOf(OtpException.class);
    }

    @Test
    void consumeRejectsAnUnverifiedChallengeAndAnUnknownId() {
        PhoneVerification unverified = challenge(0, LocalDateTime.now().plusMinutes(5));
        when(repository.findByIdAndPhone(unverified.getId(), PHONE)).thenReturn(Optional.of(unverified));
        UUID unknown = UUID.randomUUID();
        when(repository.findByIdAndPhone(unknown, PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().consumeVerification(PHONE, unverified.getId())).isInstanceOf(OtpException.class);
        assertThatThrownBy(() -> service().consumeVerification(PHONE, unknown)).isInstanceOf(OtpException.class);
        assertThatThrownBy(() -> service().consumeVerification(PHONE, null)).isInstanceOf(OtpException.class);
    }

    @Test
    void consumeRejectsAVerificationOlderThanTheValidityWindow() {
        PhoneVerification stale = challenge(0, LocalDateTime.now().plusMinutes(5));
        stale.setVerifiedAt(LocalDateTime.now().minusMinutes(60));
        when(repository.findByIdAndPhone(stale.getId(), PHONE)).thenReturn(Optional.of(stale));

        assertThatThrownBy(() -> service().consumeVerification(PHONE, stale.getId())).isInstanceOf(OtpException.class);
    }
}
