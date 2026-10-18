package com.urbantransport.fleet.exception;

public final class ErrorCodes {

    public static final String PLATE_ALREADY_EXISTS = "PLATE_ALREADY_EXISTS";
    public static final String INVALID_CAPACITY = "INVALID_CAPACITY";
    public static final String INVALID_YEAR = "INVALID_YEAR";
    public static final String INVALID_STATUS = "INVALID_STATUS";
    public static final String BRANCH_NOT_FOUND = "BRANCH_NOT_FOUND";
    public static final String BRANCH_INACTIVE = "BRANCH_INACTIVE";
    public static final String BRANCH_REQUIRED = "BRANCH_REQUIRED";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String BUS_NOT_FOUND = "BUS_NOT_FOUND";
    public static final String BUS_NOT_IN_BRANCH = "BUS_NOT_IN_BRANCH";
    public static final String BUS_INACTIVE = "BUS_INACTIVE";
    public static final String BUS_IN_TRANSIT = "BUS_IN_TRANSIT";
    public static final String BRANCH_FORBIDDEN = "BRANCH_FORBIDDEN";
    public static final String BUS_HAS_ACTIVE_TRIPS = "BUS_HAS_ACTIVE_TRIPS";

    private ErrorCodes() {
    }
}
