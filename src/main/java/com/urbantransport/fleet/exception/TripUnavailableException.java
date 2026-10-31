package com.urbantransport.fleet.exception;

public class TripUnavailableException extends RuntimeException {

    public TripUnavailableException(String message) {
        super(message);
    }

    public TripUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
