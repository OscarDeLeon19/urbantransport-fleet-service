package com.urbantransport.fleet.service;

import com.urbantransport.fleet.dto.CreateDriverRequest;
import com.urbantransport.fleet.dto.DriverCreatedResponse;

import java.util.UUID;

public interface DriverService {

    DriverCreatedResponse register(CreateDriverRequest request, UUID branchId);
}
