package com.urbantransport.fleet.service;

import com.urbantransport.fleet.domain.BusStatus;
import com.urbantransport.fleet.dto.BusResponse;
import com.urbantransport.fleet.dto.CreateBusRequest;
import com.urbantransport.fleet.dto.PageResponse;
import com.urbantransport.fleet.dto.UpdateBusRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface BusService {

    BusResponse create(CreateBusRequest request, UUID branchId);

    BusResponse update(UUID id, UpdateBusRequest request, UUID branchId);

    PageResponse<BusResponse> search(BusStatus status, UUID branchId, int page, int size);

    BusResponse findById(UUID id, UUID scopeBranchId);

    BusResponse deactivate(UUID id, UUID branchId);

    BusResponse uploadPhoto(UUID id, MultipartFile file, UUID branchId);
}
