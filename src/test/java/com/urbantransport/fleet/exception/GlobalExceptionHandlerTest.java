package com.urbantransport.fleet.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private static final String PATH = "/buses/11111111-1111-1111-1111-111111111111";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);

    @Test
    void apiException_keepsItsStatusCodeAndMessage() {
        ApiException exception = new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.BUS_HAS_ACTIVE_TRIPS,
                "El bus tiene viajes programados o en tránsito");

        ResponseEntity<ErrorResponse> response = handler.handleApi(exception, request);

        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, ErrorCodes.BUS_HAS_ACTIVE_TRIPS,
                "El bus tiene viajes programados o en tránsito");
    }

    @Test
    void validationErrors_areJoinedByField() {
        BindingResult bindingResult = new BeanPropertyBindingResult(Map.of(), "request");
        bindingResult.addError(new FieldError("request", "plate", "no debe estar vacío"));
        bindingResult.addError(new FieldError("request", "seatRows", "debe ser mayor que 0"));
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(exception, request);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                "plate: no debe estar vacío; seatRows: debe ser mayor que 0");
    }

    @Test
    void unreadableBody_returnsValidationError() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("JSON inválido",
                new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ErrorResponse> response = handler.handleUnreadable(exception, request);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, "Cuerpo de la petición inválido");
    }

    @Test
    void accessDenied_returnsForbidden() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(new AccessDeniedException("denegado"),
                request);

        assertError(response, HttpStatus.FORBIDDEN, ErrorCodes.ACCESS_DENIED, "No tienes permiso para esta operación");
    }

    @Test
    void photoTooLarge_returnsContentTooLarge() {
        ResponseEntity<ErrorResponse> response = handler.handleUploadSize(
                new MaxUploadSizeExceededException(5 * 1024 * 1024), request);

        assertError(response, HttpStatus.CONTENT_TOO_LARGE, ErrorCodes.PHOTO_TOO_LARGE,
                "La foto supera el tamaño máximo de 5MB");
    }

    @Test
    void missingPhotoPart_returnsInvalidPhoto() {
        ResponseEntity<ErrorResponse> response = handler.handleMissingPart(
                new MissingServletRequestPartException("file"), request);

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_PHOTO, "La foto es obligatoria");
    }

    @Test
    void integrityViolation_returnsConflictWithoutCode() {
        ResponseEntity<ErrorResponse> response = handler.handleIntegrity(
                new DataIntegrityViolationException("duplicate key value"), request);

        assertError(response, HttpStatus.CONFLICT, null, "El registro ya existe o viola una restricción");
    }

    private static void assertError(ResponseEntity<ErrorResponse> response, HttpStatus status, String code,
                                    String message) {
        assertEquals(status, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertNotNull(body.getTimestamp());
        assertEquals(status.value(), body.getStatus());
        assertEquals(code, body.getCode());
        assertEquals(message, body.getMessage());
        assertEquals(PATH, body.getPath());
    }
}
