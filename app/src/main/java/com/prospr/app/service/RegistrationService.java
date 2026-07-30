package com.prospr.app.service;

import com.prospr.app.dto.request.RegisterRequest;
import com.prospr.app.dto.response.RegisterResponse;

public interface RegistrationService {

    RegisterResponse register(RegisterRequest request);
}
