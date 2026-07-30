package com.prospr.app.service;

import org.springframework.stereotype.Service;

@Service
public class SmsService {

    public void sendOtp(String mobile, String otp) {

        // Replace with real MSG91 / Twilio API call
        System.out.println("Sending OTP " + otp + " to " + mobile);
    }
}
