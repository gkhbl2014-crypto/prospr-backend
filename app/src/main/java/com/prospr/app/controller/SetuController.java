/*
 * package com.prospr.app.controller;
 * 
 * import org.springframework.beans.factory.annotation.Autowired; import
 * org.springframework.web.bind.annotation.PostMapping; import
 * org.springframework.web.bind.annotation.RequestBody; import
 * org.springframework.web.bind.annotation.RequestMapping; import
 * org.springframework.web.bind.annotation.RestController;
 * 
 * import com.prospr.app.dto.ConsentRequest; import
 * com.prospr.app.dto.ConsentResponse; import
 * com.prospr.app.service.SetuConsentService;
 * 
 * @RestController
 * 
 * @RequestMapping("/api/setu")
 * 
 * public class SetuController {
 * 
 * @Autowired private SetuConsentService service;
 * 
 * @PostMapping("/consent") public ConsentResponse consent(
 * 
 * @RequestBody ConsentRequest request){
 * 
 * return service.createConsent( request.getMobile());
 * 
 * }
 * 
 * 
 * }
 */