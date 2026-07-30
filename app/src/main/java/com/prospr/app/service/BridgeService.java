package com.prospr.app.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class BridgeService {

    @Value("${setu.base-url}")
    private String baseUrl;

    @Value("${setu.client-id}")
    private String clientId;

    @Value("${setu.client-secret}")
    private String clientSecret;

    @Value("${setu.product-instance-id}")
    private String productInstanceId;

    @Value("${setu.redirect-url}")
    private String redirectUrl;

    @Autowired
    private RestTemplate restTemplate;

    public String createBridgeSession(String mobile) {

        HttpHeaders headers = new HttpHeaders();

        headers.setBasicAuth(clientId, clientSecret);

        headers.set("x-product-instance-id", productInstanceId);

        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String,Object> body = new HashMap<>();

        body.put("mobile", mobile);

        body.put("redirectUrl", redirectUrl);

        HttpEntity<Map<String,Object>> entity =
                new HttpEntity<>(body, headers);

        ResponseEntity<String> response =
                restTemplate.exchange(
                        baseUrl + "/api/bridge/session",
                        HttpMethod.POST,
                        entity,
                        String.class);

        return response.getBody();

    }

}
