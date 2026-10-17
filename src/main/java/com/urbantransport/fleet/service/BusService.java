package com.urbantransport.fleet.service;

import com.urbantransport.fleet.dto.BusResponse;
import com.urbantransport.fleet.dto.CreateBusRequest;

import java.util.UUID;

public interface BusService {

    BusResponse create(CreateBusRequest request, UUID branchId);
}
