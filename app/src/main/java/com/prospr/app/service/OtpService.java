/*
 * package com.prospr.app.auth.service;
 * 
 * @Service public class OtpService {
 * 
 * @Autowired private RedisTemplate<String, String> redisTemplate;
 * 
 * private static final long OTP_EXPIRY = 5 * 60; // 5 min
 * 
 * public String generateOtp(String mobile) {
 * 
 * String otp = String.valueOf((int)(Math.random() * 900000) + 100000);
 * 
 * redisTemplate.opsForValue().set( "OTP:" + mobile, otp,
 * Duration.ofSeconds(OTP_EXPIRY) );
 * 
 * return otp; }
 * 
 * public boolean verifyOtp(String mobile, String otp) {
 * 
 * String key = "OTP:" + mobile;
 * 
 * String storedOtp = redisTemplate.opsForValue().get(key);
 * 
 * if (storedOtp == null) return false;
 * 
 * if (storedOtp.equals(otp)) { redisTemplate.delete(key); return true; }
 * 
 * return false; } }
 */


