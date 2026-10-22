package com.urbantransport.fleet.service.impl;

import com.urbantransport.fleet.config.S3Properties;
import com.urbantransport.fleet.domain.PhotoOwner;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import com.urbantransport.fleet.service.PhotoStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoStorageServiceImpl implements PhotoStorageService {

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    @Override
    public String upload(PhotoOwner owner, UUID ownerId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_PHOTO, "La foto es obligatoria");
        }
        String extension = ALLOWED_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_PHOTO,
                    "Formato no permitido, solo JPG, PNG o WEBP");
        }

        String key = owner.getPrefix() + "/" + ownerId + "/" + UUID.randomUUID() + "." + extension;
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        try (InputStream content = file.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(content, file.getSize()));
            return key;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_PHOTO, "No se pudo leer la foto");
        } catch (SdkException exception) {
            log.error("No se pudo subir la foto {} a S3", key, exception);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.STORAGE_UNAVAILABLE,
                    "No se pudo guardar la foto, intenta más tarde");
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
        } catch (SdkException exception) {
            log.warn("No se pudo borrar la foto {} de S3", key, exception);
        }
    }

    @Override
    public String presignedUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(properties.presignedUrlTtl())
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .build())
                .build();
        return s3Presigner.presignGetObject(request).url().toString();
    }
}
