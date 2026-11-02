package com.urbantransport.fleet.client;

import com.urbantransport.fleet.config.IdentityClientProperties;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.exception.IdentityUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IdentityClientImplTest {

    private static final String BASE_URL = "http://identity.test";
    private static final String URL = BASE_URL + "/internal/users/driver";
    private static final String API_KEY = "clave-interna";
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EMAIL = "juan@mail.com";
    private static final String FULL_NAME = "Juan Pérez";
    private static final IdentityCreateUserRequest REQUEST = new IdentityCreateUserRequest(EMAIL, FULL_NAME);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private MockRestServiceServer server;
    private RestClient restClient;
    private CircuitBreaker circuitBreaker;
    private IdentityClientImpl identityClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        circuitBreaker = CircuitBreaker.of("identity", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .recordExceptions(IdentityUnavailableException.class)
                .build());
        identityClient = clientWithKey(API_KEY);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void createdAccount_isReturned() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Key", API_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.fullName").value(FULL_NAME))
                .andRespond(withSuccess("""
                        {"id":"%s","email":"%s","fullName":"%s","role":"DRIVER","active":true}
                        """.formatted(USER_ID, EMAIL, FULL_NAME), MediaType.APPLICATION_JSON));

        IdentityUserResponse response = identityClient.createDriverUser(REQUEST);

        assertEquals(USER_ID, response.id());
        assertEquals(EMAIL, response.email());
        assertEquals(FULL_NAME, response.fullName());
        assertEquals("DRIVER", response.role());
    }

    @Test
    void missingApiKey_sendsEmptyHeader() {
        server.expect(requestTo(URL))
                .andExpect(header("X-Internal-Key", ""))
                .andRespond(withSuccess("{\"id\":\"%s\"}".formatted(USER_ID), MediaType.APPLICATION_JSON));

        IdentityUserResponse response = clientWithKey(null).createDriverUser(REQUEST);

        assertEquals(USER_ID, response.id());
    }

    @Test
    void conflict_returnsMessageFromIdentity() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"EMAIL_ALREADY_EXISTS\",\"message\":\"El correo ya está registrado\"}"));

        ApiException exception = assertApiError(HttpStatus.CONFLICT, ErrorCodes.USER_EMAIL_ALREADY_EXISTS,
                () -> identityClient.createDriverUser(REQUEST));

        assertEquals("El correo ya está registrado", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"no es json", "{\"code\":\"EMAIL_ALREADY_EXISTS\"}"})
    void conflictWithoutMessage_usesDefaultMessage(String body) {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(body));

        ApiException exception = assertApiError(HttpStatus.CONFLICT, ErrorCodes.USER_EMAIL_ALREADY_EXISTS,
                () -> identityClient.createDriverUser(REQUEST));

        assertEquals("No se pudo crear la cuenta: los datos ya están en uso", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404})
    void otherClientError_throwsBadGateway(int status) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertApiError(HttpStatus.BAD_GATEWAY, ErrorCodes.IDENTITY_ERROR,
                () -> identityClient.createDriverUser(REQUEST));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503})
    void serverError_throwsServiceUnavailable(int status) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.IDENTITY_UNAVAILABLE,
                () -> identityClient.createDriverUser(REQUEST));
    }

    @Test
    void connectionFailure_throwsServiceUnavailable() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.IDENTITY_UNAVAILABLE,
                () -> identityClient.createDriverUser(REQUEST));
    }

    @Test
    void openCircuit_failsFastWithoutCallingIdentity() {
        circuitBreaker.transitionToOpenState();

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.IDENTITY_UNAVAILABLE,
                () -> identityClient.createDriverUser(REQUEST));
    }

    @Test
    void repeatedServerErrors_openCircuit() {
        server.expect(ExpectedCount.times(2), requestTo(URL)).andRespond(withServerError());

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.IDENTITY_UNAVAILABLE,
                () -> identityClient.createDriverUser(REQUEST));
        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.IDENTITY_UNAVAILABLE,
                () -> identityClient.createDriverUser(REQUEST));

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.IDENTITY_UNAVAILABLE,
                () -> identityClient.createDriverUser(REQUEST));
    }

    @Test
    void repeatedConflicts_doNotOpenCircuit() {
        server.expect(ExpectedCount.times(2), requestTo(URL)).andRespond(withStatus(HttpStatus.CONFLICT));

        assertApiError(HttpStatus.CONFLICT, ErrorCodes.USER_EMAIL_ALREADY_EXISTS,
                () -> identityClient.createDriverUser(REQUEST));
        assertApiError(HttpStatus.CONFLICT, ErrorCodes.USER_EMAIL_ALREADY_EXISTS,
                () -> identityClient.createDriverUser(REQUEST));

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    private IdentityClientImpl clientWithKey(String apiKey) {
        IdentityClientProperties properties = new IdentityClientProperties(BASE_URL, apiKey, Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                new IdentityClientProperties.CircuitBreakerProperties(2, 2, 50f, Duration.ofSeconds(30)));
        return new IdentityClientImpl(restClient, circuitBreaker, jsonMapper, properties);
    }

    private static ApiException assertApiError(HttpStatus status, String code, Executable action) {
        ApiException exception = assertThrows(ApiException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
        return exception;
    }
}
