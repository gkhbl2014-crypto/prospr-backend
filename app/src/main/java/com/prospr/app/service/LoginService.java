package com.prospr.app.service;

import com.prospr.app.dto.request.LoginRequest;
import com.prospr.app.dto.response.LoginResponse;

public interface LoginService {

    LoginResponse login(LoginRequest request);
}
