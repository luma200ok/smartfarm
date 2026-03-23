package com.smartfarm.server.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 캐시 설정.
 * JDK 직렬화 대신 JSON 직렬화를 사용하여 클래스 변경 시 ClassCastException을 방지합니다.
 * CacheErrorHandler를 등록하여 역직렬화 실패 시 예외 대신 캐시 미스로 처리합니다.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 타입 정보를 JSON에 포함시켜 역직렬화 시 올바른 타입으로 복원
        // NON_FINAL 대신 EVERYTHING 사용 — record는 암묵적 final이라
        // NON_FINAL로는 @class 타입 정보가 추가되지 않아 역직렬화 실패 발생
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * 캐시 조회/저장 중 예외 발생 시 서버 전체가 중단되지 않도록 예외를 흡수합니다.
     * - getLookupError : 역직렬화 실패(@class 없음, 구 JDK 형식 등) → 캐시 미스로 처리 (DB 재조회)
     * - putError / evictError / clearError : Redis 저장/삭제 실패 → 경고 로그만 남김
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("[Cache] 캐시 조회 실패 — 캐시 미스로 처리합니다. cache={}, key={}, error={}",
                        cache.getName(), key, e.getMessage());
                // 예외를 삼켜 캐시 미스로 처리 → @Cacheable 메서드가 DB를 재조회
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("[Cache] 캐시 저장 실패. cache={}, key={}, error={}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("[Cache] 캐시 삭제 실패. cache={}, key={}, error={}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("[Cache] 캐시 전체 삭제 실패. cache={}, error={}",
                        cache.getName(), e.getMessage());
            }
        };
    }
}
