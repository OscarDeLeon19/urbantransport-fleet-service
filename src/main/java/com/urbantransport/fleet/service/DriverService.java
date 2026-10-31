package com.urbantransport.fleet.service;

import com.urbantransport.fleet.dto.CreateDriverRequest;
import com.urbantransport.fleet.dto.DriverCreatedResponse;
import com.urbantransport.fleet.dto.DriverResponse;
import com.urbantransport.fleet.dto.PageResponse;
import com.urbantransport.fleet.dto.UpdateDriverRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface DriverService {

    DriverCreatedResponse register(CreateDriverRequest request, UUID branchId);

    DriverResponse update(UUID id, UpdateDriverRequest request, UUID branchId);

    DriverResponse deactivate(UUID id, UUID branchId);

    PageResponse<DriverResponse> search(Boolean active, Integer expiringInDays, UUID branchId, int page, int size);

    DriverResponse findById(UUID id, UUID scopeBranchId);

    DriverResponse findByUserId(UUID userId);

    DriverResponse uploadPhoto(UUID id, MultipartFile file, UUID branchId);
}
