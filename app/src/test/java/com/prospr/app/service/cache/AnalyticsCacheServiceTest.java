package com.prospr.app.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.prospr.app.config.RedisCacheProperties;
import com.prospr.app.dto.response.LifestyleInsightResponse;
import com.prospr.app.dto.response.SafetyNetResponse;

@ExtendWith(MockitoExtension.class)
class AnalyticsCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private final RedisCacheProperties properties = new RedisCacheProperties();

    private AnalyticsCacheService service() {
        return new AnalyticsCacheService(redisTemplate, properties);
    }

    private UUID memberId() {
        return UUID.randomUUID();
    }

    // ---- cache hit / miss --------------------------------------------------------------------

    @Test
    void cacheHitReturnsCachedValueWithoutRecomputing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UUID memberId = memberId();
        List<LifestyleInsightResponse> cached = List.of(LifestyleInsightResponse.builder().category("DINING").build());
        when(valueOperations.get(CacheKeys.lifestyleCreep(memberId))).thenReturn(cached);

        Optional<List<LifestyleInsightResponse>> result = service().getLifestyleCreep(memberId);

        assertThat(result).contains(cached);
    }

    @Test
    void cacheMissReturnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UUID memberId = memberId();
        when(valueOperations.get(CacheKeys.lifestyleCreep(memberId))).thenReturn(null);

        Optional<List<LifestyleInsightResponse>> result = service().getLifestyleCreep(memberId);

        assertThat(result).isEmpty();
    }

    @Test
    void putStoresValueWithConfiguredTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UUID memberId = memberId();
        SafetyNetResponse value = SafetyNetResponse.builder().build();
        properties.setSafetyNetTtlMinutes(45);

        service().putSafetyNet(memberId, value);

        verify(valueOperations).set(eq(CacheKeys.safetyNet(memberId)), eq(value), eq(Duration.ofMinutes(45)));
    }

    @Test
    void evictDeletesTheKey() {
        UUID memberId = memberId();

        service().evictSpendingSummary(memberId);

        verify(redisTemplate).delete(CacheKeys.spendingSummary(memberId));
    }

    // ---- Redis failure never propagates -------------------------------------------------------

    @Test
    void getSwallowsRedisExceptionAndReturnsEmpty() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("connection refused"));

        Optional<List<LifestyleInsightResponse>> result = service().getLifestyleCreep(memberId());

        assertThat(result).isEmpty();
    }

    @Test
    void putSwallowsRedisExceptionRatherThanThrowing() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("timeout"));

        // Must not throw.
        service().putSafetyNet(memberId(), SafetyNetResponse.builder().build());
    }

    @Test
    void evictSwallowsRedisExceptionRatherThanThrowing() {
        when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("timeout"));

        service().evictLifestyleCreep(memberId());
    }

    // ---- global enable/disable switch ----------------------------------------------------------

    @Test
    void disabledCacheNeverTouchesRedisAtAll() {
        properties.setEnabled(false);

        Optional<List<LifestyleInsightResponse>> result = service().getLifestyleCreep(memberId());
        service().putLifestyleCreep(memberId(), List.of());
        service().evictLifestyleCreep(memberId());

        assertThat(result).isEmpty();
        verifyNoInteractions(redisTemplate);
    }

    // ---- family/member isolation ----------------------------------------------------------------

    @Test
    void differentMembersNeverShareACacheKey() {
        UUID memberA = memberId();
        UUID memberB = memberId();

        assertThat(CacheKeys.safetyNet(memberA)).isNotEqualTo(CacheKeys.safetyNet(memberB));
        assertThat(CacheKeys.lifestyleCreep(memberA)).isNotEqualTo(CacheKeys.lifestyleCreep(memberB));
        assertThat(CacheKeys.spendingSummary(memberA)).isNotEqualTo(CacheKeys.spendingSummary(memberB));
    }

    // ---- stampede guard -----------------------------------------------------------------------

    @Test
    void withStampedeGuardReturnsTheLoaderResult() {
        String result = service().withStampedeGuard("some-key", () -> "computed");

        assertThat(result).isEqualTo("computed");
    }

    @Test
    void withStampedeGuardSerializesConcurrentCallsOnTheSameKey() throws InterruptedException {
        AnalyticsCacheService cacheService = service();
        String key = "shared-key";
        AtomicInteger concurrentRunners = new AtomicInteger(0);
        AtomicInteger maxObservedConcurrency = new AtomicInteger(0);
        CountDownLatch bothStarted = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Runnable task = () -> cacheService.withStampedeGuard(key, () -> {
            int current = concurrentRunners.incrementAndGet();
            maxObservedConcurrency.updateAndGet(max -> Math.max(max, current));
            bothStarted.countDown();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            concurrentRunners.decrementAndGet();
            return null;
        });

        executor.submit(task);
        executor.submit(task);
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);

        assertThat(maxObservedConcurrency.get()).isEqualTo(1);
    }
}
