package com.urbantransport.fleet.service;

import com.urbantransport.fleet.domain.PhotoOwner;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface PhotoStorageService {

    String upload(PhotoOwner owner, UUID ownerId, MultipartFile file);

    void delete(String key);

    String presignedUrl(String key);
}
