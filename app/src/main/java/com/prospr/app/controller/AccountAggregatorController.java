/*
 * package com.prospr.app.auth.controller;
 * 
 * import org.springframework.http.ResponseEntity; import
 * org.springframework.web.bind.annotation.PostMapping; import
 * org.springframework.web.bind.annotation.RequestMapping; import
 * org.springframework.web.bind.annotation.RestController;
 * 
 * import com.prospr.app.auth.dto.ConsentResponse; import
 * com.prospr.app.auth.service.SetuService; import
 * com.prospr.app.common.ApiResponse;
 * 
 * import lombok.RequiredArgsConstructor;
 * 
 * @RestController
 * 
 * @RequestMapping("/api/aa")
 * 
 * @RequiredArgsConstructor public class AccountAggregatorController {
 * 
 * private final SetuService setuService;
 * 
 * @PostMapping("/connect") public ResponseEntity<ApiResponse<ConsentResponse>>
 * connect() {
 * 
 * ConsentResponse response = setuService.createConsent();
 * 
 * ApiResponse<ConsentResponse> api = new ApiResponse<>();
 * 
 * api.setSuccess(true); api.setMessage("Consent URL Generated");
 * api.setData(response);
 * 
 * return ResponseEntity.ok(api);
 * 
 * }
 * 
 * }
 */


