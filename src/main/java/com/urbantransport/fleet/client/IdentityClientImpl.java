package com.urbantransport.fleet.client;

import com.urbantransport.fleet.config.IdentityClientProperties;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.exception.IdentityUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityClientImpl implements IdentityClient {

    private static final String CREATE_DRIVER_PATH = "/internal/users/driver";
    private static final String INTERNAL_KEY_HEADER = "X-Internal-Key";
    private static final String UNAVAILABLE_MESSAGE =
            "El servicio de autenticación no está disponible, intenta más tarde";

    private final RestClient identityRestClient;
    private final CircuitBreaker identityCircuitBreaker;
    private final JsonMapper jsonMapper;
    private final IdentityClientProperties properties;

    @Override
    public IdentityUserResponse createDriverUser(IdentityCreateUserRequest request) {
        try {
            return identityCircuitBreaker.executeSupplier(() -> post(request));
        } catch (CallNotPermittedException | IdentityUnavailableException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.IDENTITY_UNAVAILABLE, UNAVAILABLE_MESSAGE);
        }
    }

    private IdentityUserResponse post(IdentityCreateUserRequest request) {
        try {
            String apiKey = properties.apiKey();
            return identityRestClient.post()
                    .uri(CREATE_DRIVER_PATH)
                    .header(INTERNAL_KEY_HEADER, apiKey == null ? "" : apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(status -> status.is5xxServerError(), (req, res) -> {
                        throw new IdentityUnavailableException("Identity respondió " + res.getStatusCode());
                    })
                    .onStatus(status -> status.value() == HttpStatus.CONFLICT.value(), (req, res) -> {
                        throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.USER_EMAIL_ALREADY_EXISTS, extractMessage(res));
                    })
                    .onStatus(status -> status.is4xxClientError(), (req, res) -> {
                        log.error("Identity rechazó la creación de cuenta: {} (revise INTERNAL_API_KEY)", res.getStatusCode());
                        throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCodes.IDENTITY_ERROR,
                                "No se pudo crear la cuenta, intenta más tarde");
                    })
                    .body(IdentityUserResponse.class);
        } catch (ResourceAccessException exception) {
            throw new IdentityUnavailableException("No se pudo contactar a identity", exception);
        }
    }

    private String extractMessage(ClientHttpResponse response) {
        try {
            Map<?, ?> body = jsonMapper.readValue(response.getBody(), Map.class);
            if (body != null && body.get("message") != null) {
                return body.get("message").toString();
            }
        } catch (Exception ignored) {
        }
        return "No se pudo crear la cuenta: los datos ya están en uso";
    }
}
