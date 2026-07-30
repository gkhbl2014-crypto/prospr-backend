package com.prospr.app.config;



public class JwtConfig {
    public static final String SECRET = "my-super-secret-key-change-this-in-prod";
    public static final long EXPIRATION = 1000 * 60 * 60 * 10; // 10 hours
}
