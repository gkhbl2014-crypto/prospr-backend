package com.prospr.app.service;

import java.util.List;

import com.prospr.app.dto.request.FamilyLoginRequest;
import com.prospr.app.dto.request.LoginRequest;
import com.prospr.app.dto.response.FamilyMemberSummaryResponse;
import com.prospr.app.dto.response.LoginResponse;

public interface LoginService {

    LoginResponse login(LoginRequest request);

    List<FamilyMemberSummaryResponse> listFamilyMembers(String inviteCode);

    LoginResponse familyLogin(FamilyLoginRequest request);
}
