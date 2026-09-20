package com.prospr.app.service.cache;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.prospr.app.config.RedisCacheProperties;
import com.prospr.app.dto.response.LifestyleInsightResponse;
import com.prospr.app.dto.response.MonthlySnapshotResponse;
import com.prospr.app.dto.response.SafetyNetResponse;

/**
 * Cache-aside layer in front of Postgres for the three analytics domains that have real backend
 * computation today (Lifestyle Creep, Safety Net, Spending Summary). Postgres remains the source of
 * truth: every get/put/evict here is defensive - a Redis outage, timeout, or serialization failure
 * is logged and swallowed, never thrown, so callers always fall through to their existing
 * Postgres-backed logic exactly as if this class didn't exist. See {@link CacheKeys} for the key
 * convention and {@link RedisCacheProperties} for TTLs and the global enable/disable switch.
 */
@Service
public class AnalyticsCacheService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsCacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisCacheProperties properties;
    private final ConcurrentHashMap<String, Object> stampedeLocks = new ConcurrentHashMap<>();

    public AnalyticsCacheService(RedisTemplate<String, Object> analyticsRedisTemplate, RedisCacheProperties properties) {
        this.redisTemplate = analyticsRedisTemplate;
        this.properties = properties;
    }

    // ---- Lifestyle Creep ----------------------------------------------------------------------

    public Optional<List<LifestyleInsightResponse>> getLifestyleCreep(UUID memberId) {
        return Optional.ofNullable(read(CacheKeys.lifestyleCreep(memberId)));
    }

    public void putLifestyleCreep(UUID memberId, List<LifestyleInsightResponse> value) {
        write(CacheKeys.lifestyleCreep(memberId), value, Duration.ofMinutes(properties.getLifestyleCreepTtlMinutes()));
    }

    public void evictLifestyleCreep(UUID memberId) {
        evict(CacheKeys.lifestyleCreep(memberId));
    }

    // ---- Safety Net -----------------------------------------------------------------------------

    public Optional<SafetyNetResponse> getSafetyNet(UUID memberId) {
        return Optional.ofNullable(read(CacheKeys.safetyNet(memberId)));
    }

    public void putSafetyNet(UUID memberId, SafetyNetResponse value) {
        write(CacheKeys.safetyNet(memberId), value, Duration.ofMinutes(properties.getSafetyNetTtlMinutes()));
    }

    public void evictSafetyNet(UUID memberId) {
        evict(CacheKeys.safetyNet(memberId));
    }

    // ---- Spending Summary -------------------------------------------------------------------------

    public Optional<List<MonthlySnapshotResponse>> getSpendingSummary(UUID memberId) {
        return Optional.ofNullable(read(CacheKeys.spendingSummary(memberId)));
    }

    public void putSpendingSummary(UUID memberId, List<MonthlySnapshotResponse> value) {
        write(CacheKeys.spendingSummary(memberId), value, Duration.ofMinutes(properties.getSpendingSummaryTtlMinutes()));
    }

    public void evictSpendingSummary(UUID memberId) {
        evict(CacheKeys.spendingSummary(memberId));
    }

    // ---- Stampede protection ----------------------------------------------------------------------

    /**
     * Simple in-JVM (not distributed - this is a single-instance app) guard against many concurrent
     * requests all recomputing the same expensive analytics after a cache expiry. The first caller
     * through for a given key runs {@code loader}; any others arriving while it's running block on
     * the same monitor and should re-check the cache themselves once they acquire it (callers are
     * expected to re-check-then-compute inside {@code loader}, i.e. this is a lock, not a memoizer).
     */
    public <T> T withStampedeGuard(String key, Supplier<T> loader) {
        Object lock = stampedeLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            try {
                return loader.get();
            } finally {
                stampedeLocks.remove(key, lock);
            }
        }
    }

    // ---- Generic read/write/evict, defensive against any Redis failure ---------------------------

    @SuppressWarnings("unchecked")
    private <T> T read(String key) {
        if (!properties.isEnabled()) {
            return null;
        }
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                log.debug("Cache miss key={}", key);
                return null;
            }
            log.debug("Cache hit key={}", key);
            return (T) value;
        } catch (Exception ex) {
            log.warn("Redis read failed, falling back to source. key={} error={}", key, ex.getClass().getSimpleName());
            return null;
        }
    }

    private void write(String key, Object value, Duration ttl) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            log.debug("Cache write key={} ttlMinutes={}", key, ttl.toMinutes());
        } catch (Exception ex) {
            log.warn("Redis write failed, continuing without cache. key={} error={}", key, ex.getClass().getSimpleName());
        }
    }

    private void evict(String key) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            boolean deleted = Boolean.TRUE.equals(redisTemplate.delete(key));
            log.debug("Cache evict key={} deleted={}", key, deleted);
        } catch (Exception ex) {
            log.warn("Redis evict failed. key={} error={}", key, ex.getClass().getSimpleName());
        }
    }
}
