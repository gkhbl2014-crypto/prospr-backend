package com.prospr.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class RedisConfig {

    /** Unused today (an OTP flow stubbed out in {@code OtpService} that was never wired up) - left
     *  as-is rather than repurposed, so nothing about the analytics cache below risks it. */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        return template;
    }

    /**
     * JSON-backed template for {@code AnalyticsCacheService}. String keys (readable in redis-cli),
     * JSON values via Jackson rather than Java native serialization.
     *
     * <p>Deliberately does NOT reuse the app's REST-layer {@code ObjectMapper} bean as-is: on its
     * own, a plain {@code ObjectMapper} (even with {@code JavaTimeModule} registered) never embeds
     * the {@code @class} type hint {@code GenericJackson2JsonRedisSerializer} needs to reconstruct
     * the exact DTO on read - every cached value silently deserializes back as a generic
     * {@code LinkedHashMap} instead. Nor does the serializer's bare no-arg constructor register
     * {@code JavaTimeModule} on its own internal mapper, which fails outright on any DTO containing
     * a {@code LocalDateTime}/{@code BigDecimal} date field. Both gaps were caught by
     * {@code RedisSerializationTest} exercising the real serializer end-to-end, not a mock - this
     * builds one {@code ObjectMapper} with both pieces explicitly configured, matching what Spring
     * Data Redis's own default constructor does internally, plus date/time support.
     */
    @Bean
    public RedisTemplate<String, Object> analyticsRedisTemplate(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(cacheObjectMapper());

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    /** Package-private so {@code RedisSerializationTest} can exercise this exact configuration
     *  rather than a hand-copied duplicate that could silently drift from it. */
    static ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Object.class)
                .build();
        mapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return mapper;
    }
}
