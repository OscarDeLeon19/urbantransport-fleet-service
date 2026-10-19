package com.urbantransport.fleet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.identity")
public record IdentityClientProperties(
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        CircuitBreakerProperties circuitBreaker) {

    public record CircuitBreakerProperties(
            int slidingWindowSize,
            int minimumCalls,
            float failureRateThreshold,
            Duration waitInOpenState) {
    }
}
