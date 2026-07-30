package com.prospr.app.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.BridgeSessionRequest;
import com.prospr.app.service.BridgeService;

@RestController
@RequestMapping("/api/bridge")
public class BridgeController {

    @Autowired
    private BridgeService bridgeService;

    @PostMapping("/session")
    public String createSession(
            @RequestBody BridgeSessionRequest request){

        return bridgeService.createBridgeSession(
                request.getMobile());

    }

}
