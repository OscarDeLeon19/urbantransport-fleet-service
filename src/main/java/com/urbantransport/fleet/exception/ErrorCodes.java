package com.urbantransport.fleet.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
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
    public static final String LICENSE_ALREADY_EXISTS = "LICENSE_ALREADY_EXISTS";
    public static final String LICENSE_EXPIRED = "LICENSE_EXPIRED";
    public static final String DRIVER_NOT_FOUND = "DRIVER_NOT_FOUND";
    public static final String DRIVER_NOT_IN_BRANCH = "DRIVER_NOT_IN_BRANCH";
    public static final String DRIVER_INACTIVE = "DRIVER_INACTIVE";
    public static final String DRIVER_HAS_ACTIVE_TRIPS = "DRIVER_HAS_ACTIVE_TRIPS";
    public static final String USER_EMAIL_ALREADY_EXISTS = "USER_EMAIL_ALREADY_EXISTS";
    public static final String IDENTITY_UNAVAILABLE = "IDENTITY_UNAVAILABLE";
    public static final String IDENTITY_ERROR = "IDENTITY_ERROR";
}
