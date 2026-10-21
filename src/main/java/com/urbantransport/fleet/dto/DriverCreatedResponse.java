package com.urbantransport.fleet.dto;

import lombok.Value;

@Value
public class DriverCreatedResponse {
    DriverResponse driver;
    String temporaryPassword;
}
