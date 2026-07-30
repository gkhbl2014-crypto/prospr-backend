package com.prospr.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/setu")
public class SetuCallbackController {

    @PostMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestBody String body){

        System.out.println(body);

        return ResponseEntity.ok("SUCCESS");
    }

}
