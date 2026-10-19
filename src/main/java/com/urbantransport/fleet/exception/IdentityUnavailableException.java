package com.urbantransport.fleet.exception;

public class IdentityUnavailableException extends RuntimeException {

    public IdentityUnavailableException(String message) {
        super(message);
    }

    public IdentityUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
