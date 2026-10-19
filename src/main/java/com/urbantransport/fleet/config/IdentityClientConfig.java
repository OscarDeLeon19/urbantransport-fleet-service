package com.urbantransport.fleet.config;

import com.urbantransport.fleet.exception.IdentityUnavailableException;
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
public class IdentityClientConfig {

    @Bean
    public RestClient identityRestClient(IdentityClientProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("app.identity.api-key vacío: identity rechazará la creación de cuentas (defina INTERNAL_API_KEY)");
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
    public CircuitBreaker identityCircuitBreaker(IdentityClientProperties properties) {
        IdentityClientProperties.CircuitBreakerProperties settings = properties.circuitBreaker();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("identity", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumCalls())
                .failureRateThreshold(settings.failureRateThreshold())
                .waitDurationInOpenState(settings.waitInOpenState())
                .recordExceptions(IdentityUnavailableException.class)
                .build());
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn("Circuit breaker de identity: {}", event.getStateTransition()));
        return circuitBreaker;
    }
}
