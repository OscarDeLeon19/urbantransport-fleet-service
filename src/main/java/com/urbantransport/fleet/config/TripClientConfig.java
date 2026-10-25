package com.urbantransport.fleet.config;

import com.urbantransport.fleet.exception.TripUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Slf4j
@Configuration
public class TripClientConfig {

    @Bean
    public RestClient tripRestClient(TripClientProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("app.trip.api-key vacío: trip rechazará las consultas internas (defina INTERNAL_API_KEY)");
        }

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public CircuitBreaker tripCircuitBreaker(TripClientProperties properties) {
        TripClientProperties.CircuitBreakerProperties settings = properties.circuitBreaker();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("trip", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumCalls())
                .failureRateThreshold(settings.failureRateThreshold())
                .waitDurationInOpenState(settings.waitInOpenState())
                .recordExceptions(TripUnavailableException.class)
                .build());
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn("Circuit breaker de trip: {}", event.getStateTransition()));
        return circuitBreaker;
    }
}
