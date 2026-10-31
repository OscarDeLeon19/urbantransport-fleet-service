package com.urbantransport.fleet.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorCodes {

    public static final String PLATE_ALREADY_EXISTS = "PLATE_ALREADY_EXISTS";
    public static final String INVALID_CAPACITY = "INVALID_CAPACITY";
    public static final String INVALID_SEAT_LAYOUT = "INVALID_SEAT_LAYOUT";
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
    public static final String SAME_ORIGIN_DESTINATION = "SAME_ORIGIN_DESTINATION";
    public static final String DESTINATION_BRANCH_INVALID = "DESTINATION_BRANCH_INVALID";
    public static final String ROUTE_ALREADY_EXISTS = "ROUTE_ALREADY_EXISTS";
    public static final String ROUTE_NOT_FOUND = "ROUTE_NOT_FOUND";
    public static final String ROUTE_NOT_IN_BRANCH = "ROUTE_NOT_IN_BRANCH";
    public static final String ROUTE_HAS_TRIPS = "ROUTE_HAS_TRIPS";
    public static final String INVALID_PHOTO = "INVALID_PHOTO";
    public static final String STORAGE_UNAVAILABLE = "STORAGE_UNAVAILABLE";
    public static final String PHOTO_TOO_LARGE = "PHOTO_TOO_LARGE";
    public static final String SEAT_LAYOUT_LOCKED = "SEAT_LAYOUT_LOCKED";
    public static final String TRIP_UNAVAILABLE = "TRIP_UNAVAILABLE";
    public static final String TRIP_ERROR = "TRIP_ERROR";
}
