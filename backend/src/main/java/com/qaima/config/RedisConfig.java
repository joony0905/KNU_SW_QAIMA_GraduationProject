package com.qaima.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qaima.dto.industry.IndustryIndexBlockDto;
import com.qaima.service.marketdata.model.PriceSnapshot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ObjectMapper redisObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return objectMapper;
    }

    // String
    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }

    //industry_index
    @Bean
    public ReactiveRedisTemplate<String, IndustryIndexBlockDto> industryIndexBlockRedisTemplate(
            ReactiveRedisConnectionFactory factory,
            ObjectMapper redisObjectMapper
    ) {
        Jackson2JsonRedisSerializer<IndustryIndexBlockDto> serializer =
                new Jackson2JsonRedisSerializer<>(redisObjectMapper, IndustryIndexBlockDto.class);

        RedisSerializationContext<String, IndustryIndexBlockDto> context =
                RedisSerializationContext
                        .<String, IndustryIndexBlockDto>newSerializationContext(new StringRedisSerializer())
                        .key(new StringRedisSerializer())
                        .value(serializer)
                        .hashKey(new StringRedisSerializer())
                        .hashValue(serializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    //price snapshot
    @Bean
    public ReactiveRedisTemplate<String, PriceSnapshot> priceSnapshotRedisTemplate(
            ReactiveRedisConnectionFactory factory,
            ObjectMapper redisObjectMapper
    ) {
        Jackson2JsonRedisSerializer<PriceSnapshot> serializer =
                new Jackson2JsonRedisSerializer<>(redisObjectMapper, PriceSnapshot.class);

        RedisSerializationContext<String, PriceSnapshot> context =
                RedisSerializationContext
                        .<String, PriceSnapshot>newSerializationContext(new StringRedisSerializer())
                        .key(new StringRedisSerializer())
                        .value(serializer)
                        .hashKey(new StringRedisSerializer())
                        .hashValue(serializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}