package com.prospr.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Tunables for the analytics Redis cache - see application.yml ("prospr.cache.*") for the actual
 * values, mirroring {@link LifestyleProperties}'s externalized-properties pattern. {@code enabled}
 * is a hard global bypass: when false, {@code AnalyticsCacheService} never touches Redis at all,
 * regardless of whether a connection is available.
 */
@Configuration
@ConfigurationProperties(prefix = "prospr.cache")
@Getter
@Setter
public class RedisCacheProperties {

    private boolean enabled = true;
    private long lifestyleCreepTtlMinutes = 30;
    private long safetyNetTtlMinutes = 30;
    private long spendingSummaryTtlMinutes = 30;
}
