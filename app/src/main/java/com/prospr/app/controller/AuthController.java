package com.prospr.app.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.request.FamilyLoginRequest;
import com.prospr.app.dto.request.JoinFamilyRequest;
import com.prospr.app.dto.request.LoginRequest;
import com.prospr.app.dto.request.RegisterRequest;
import com.prospr.app.dto.response.FamilyMemberSummaryResponse;
import com.prospr.app.dto.response.JoinFamilyResponse;
import com.prospr.app.dto.response.LoginResponse;
import com.prospr.app.dto.response.RegisterResponse;
import com.prospr.app.service.LoginService;
import com.prospr.app.service.RegistrationService;

import jakarta.validation.Valid;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final RegistrationService registrationService;
    private final LoginService loginService;

    public AuthController(RegistrationService registrationService, LoginService loginService) {
        this.registrationService = registrationService;
        this.loginService = loginService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Received registration request for email '{}'", request.getEmail());
        RegisterResponse response = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Adds a new member to an existing family using that family's invite code. */
    @PostMapping("/join")
    public ResponseEntity<JoinFamilyResponse> join(@Valid @RequestBody JoinFamilyRequest request) {
        log.info("Received join-family request for email '{}'", request.getEmail());
        JoinFamilyResponse response = registrationService.joinFamily(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Received login request for email '{}'", request.getEmail());
        LoginResponse response = loginService.login(request);
        return ResponseEntity.ok(response);
    }

    /** Public member picker for the family-code login screen - names/roles only, no credentials. */
    @GetMapping("/family/{inviteCode}/members")
    public ResponseEntity<List<FamilyMemberSummaryResponse>> listFamilyMembers(@PathVariable String inviteCode) {
        return ResponseEntity.ok(loginService.listFamilyMembers(inviteCode));
    }

    @PostMapping("/family-login")
    public ResponseEntity<LoginResponse> familyLogin(@Valid @RequestBody FamilyLoginRequest request) {
        log.info("Received family-code login request for memberId '{}'", request.getMemberId());
        LoginResponse response = loginService.familyLogin(request);
        return ResponseEntity.ok(response);
    }
}
