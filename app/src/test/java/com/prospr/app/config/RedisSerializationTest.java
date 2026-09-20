package com.prospr.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

import com.prospr.app.dto.response.CategoryAmount;
import com.prospr.app.dto.response.LifestyleInsightResponse;
import com.prospr.app.dto.response.MonthlySnapshotResponse;
import com.prospr.app.dto.response.SafetyNetResponse;

/**
 * Exercises the real {@link GenericJackson2JsonRedisSerializer} (no mocking) against every DTO
 * {@code AnalyticsCacheService} caches. This is the test that would have caught the
 * builder-without-@Jacksonized gotcha found during planning: a plain {@code @Builder} DTO with no
 * setters/no-arg constructor cannot be reconstructed by Jackson from JSON at all, so caching it
 * would silently return garbage (or throw) on every single read.
 */
class RedisSerializationTest {

    // Exercises RedisConfig's actual production ObjectMapper configuration (package-private for
    // exactly this reason), not a hand-copied duplicate that could silently drift from it.
    private final RedisSerializer<Object> serializer =
            new GenericJackson2JsonRedisSerializer(RedisConfig.cacheObjectMapper());

    @Test
    void lifestyleInsightResponseRoundTripsThroughJson() {
        LifestyleInsightResponse original = LifestyleInsightResponse.builder()
                .category("DINING")
                .categoryLabel("Dining")
                .baselineAmount(new BigDecimal("4500.00"))
                .currentAmount(new BigDecimal("6200.00"))
                .differenceAmount(new BigDecimal("1700.00"))
                .increasePercentage(37.7)
                .severity("MEDIUM")
                .message("Your Dining expenses are 38% higher than usual.")
                .monthToDate(true)
                .periodLabel("Month to date (1-15 Aug)")
                .build();

        byte[] serialized = serializer.serialize(original);
        Object deserialized = serializer.deserialize(serialized);

        assertThat(deserialized).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void safetyNetResponseWithNestedBuildersRoundTripsThroughJson() {
        SafetyNetResponse original = SafetyNetResponse.builder()
                .health(SafetyNetResponse.Health.builder()
                        .individualCoverage(new BigDecimal("500000"))
                        .sharedCoverage(BigDecimal.ZERO)
                        .totalCoverage(new BigDecimal("500000"))
                        .activePolicyCount(1)
                        .status("REVIEW_RECOMMENDED")
                        .build())
                .life(SafetyNetResponse.Life.builder()
                        .totalCoverage(new BigDecimal("2000000"))
                        .estimatedTarget(new BigDecimal("3000000"))
                        .estimatedGap(new BigDecimal("1000000"))
                        .status("GAP_DETECTED")
                        .build())
                .emergencyFund(SafetyNetResponse.EmergencyFund.builder()
                        .currentAmount(new BigDecimal("100000"))
                        .averageMonthlyEssentialExpense(new BigDecimal("30000"))
                        .coverageMonths(new BigDecimal("3.33"))
                        .targetMonths(6)
                        .targetAmount(new BigDecimal("180000"))
                        .gap(new BigDecimal("80000"))
                        .status("BUILDING")
                        .build())
                .insights(List.of(SafetyNetResponse.Insight.builder()
                        .type("LIFE_GAP").severity("WARNING").message("Estimated protection gap detected.")
                        .build()))
                .calculatedAt(LocalDateTime.of(2026, 9, 20, 10, 30))
                .build();

        byte[] serialized = serializer.serialize(original);
        Object deserialized = serializer.deserialize(serialized);

        assertThat(deserialized).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void monthlySnapshotResponseWithNestedCategoryAmountsRoundTripsThroughJson() {
        MonthlySnapshotResponse original = MonthlySnapshotResponse.builder()
                .year(2026).month(8).monthLabel("August 2026")
                .totalIncome(new BigDecimal("100000")).totalEssential(new BigDecimal("30000"))
                .totalDiscretionary(new BigDecimal("20000")).totalInvestment(BigDecimal.ZERO)
                .totalDebtRepayment(BigDecimal.ZERO).totalInsurance(BigDecimal.ZERO)
                .totalInternalTransfer(BigDecimal.ZERO).totalCashWithdrawal(BigDecimal.ZERO)
                .totalUnknown(BigDecimal.ZERO).totalExpenses(new BigDecimal("50000"))
                .savings(new BigDecimal("50000")).savingsRate(new BigDecimal("50.00"))
                .transactionCount(42).averageTransactionValue(new BigDecimal("1190.48"))
                .topCategories(List.of(CategoryAmount.builder()
                        .category("DINING").topLevelCategory("Food & Dining").amount(new BigDecimal("6200"))
                        .build()))
                .hasData(true)
                .calculatedAt(LocalDateTime.of(2026, 9, 20, 10, 30))
                .build();

        byte[] serialized = serializer.serialize(original);
        Object deserialized = serializer.deserialize(serialized);

        assertThat(deserialized).usingRecursiveComparison().isEqualTo(original);
    }
}
