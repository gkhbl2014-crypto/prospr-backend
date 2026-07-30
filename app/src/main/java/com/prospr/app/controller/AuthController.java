package com.prospr.app.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.request.LoginRequest;
import com.prospr.app.dto.request.RegisterRequest;
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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Received login request for email '{}'", request.getEmail());
        LoginResponse response = loginService.login(request);
        return ResponseEntity.ok(response);
    }
}
