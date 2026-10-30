package com.urbantransport.fleet.service.impl;

import com.urbantransport.fleet.config.S3Properties;
import com.urbantransport.fleet.domain.PhotoOwner;
import com.urbantransport.fleet.exception.ApiException;
import com.urbantransport.fleet.exception.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhotoStorageServiceImplTest {

    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String BUCKET = "bucket-test";
    private static final Duration TTL = Duration.ofMinutes(60);
    private static final String KEY = "buses/11111111-1111-1111-1111-111111111111/photo.jpg";
    private static final byte[] CONTENT = {1, 2, 3, 4};

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Captor
    private ArgumentCaptor<PutObjectRequest> putCaptor;

    @Captor
    private ArgumentCaptor<DeleteObjectRequest> deleteCaptor;

    @Captor
    private ArgumentCaptor<GetObjectPresignRequest> presignCaptor;

    private PhotoStorageServiceImpl photoStorageService;

    @BeforeEach
    void setUp() {
        photoStorageService = new PhotoStorageServiceImpl(s3Client, s3Presigner,
                new S3Properties(BUCKET, "us-east-2", TTL));
    }

    @Nested
    class Upload {

        @ParameterizedTest
        @CsvSource({
                "image/jpeg, jpg, BUS, buses",
                "image/png, png, DRIVER, drivers",
                "image/webp, webp, BUS, buses"
        })
        void allowedImage_isUploadedUnderOwnerPrefix(String contentType, String extension, PhotoOwner owner,
                                                     String prefix) {
            MockMultipartFile file = new MockMultipartFile("file", "photo", contentType, CONTENT);

            String key = photoStorageService.upload(owner, OWNER_ID, file);

            assertTrue(key.matches(prefix + "/" + OWNER_ID + "/[0-9a-f-]{36}\\." + extension), key);
            verify(s3Client).putObject(putCaptor.capture(), any(RequestBody.class));
            PutObjectRequest request = putCaptor.getValue();
            assertEquals(BUCKET, request.bucket());
            assertEquals(key, request.key());
            assertEquals(contentType, request.contentType());
            assertEquals(CONTENT.length, request.contentLength());
        }

        @Test
        void missingFile_throwsInvalidPhoto() {
            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_PHOTO,
                    () -> photoStorageService.upload(PhotoOwner.BUS, OWNER_ID, null));

            verifyNoInteractions(s3Client);
        }

        @Test
        void emptyFile_throwsInvalidPhoto() {
            MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[0]);

            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_PHOTO,
                    () -> photoStorageService.upload(PhotoOwner.BUS, OWNER_ID, file));

            verifyNoInteractions(s3Client);
        }

        @ParameterizedTest
        @ValueSource(strings = {"image/gif", "application/pdf", "text/plain"})
        void notAllowedType_throwsInvalidPhoto(String contentType) {
            MockMultipartFile file = new MockMultipartFile("file", "photo", contentType, CONTENT);

            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_PHOTO,
                    () -> photoStorageService.upload(PhotoOwner.BUS, OWNER_ID, file));

            verifyNoInteractions(s3Client);
        }

        @Test
        void unreadableFile_throwsInvalidPhoto() throws IOException {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getContentType()).thenReturn("image/jpeg");
            when(file.getSize()).thenReturn((long) CONTENT.length);
            when(file.getInputStream()).thenThrow(new IOException("stream cerrado"));

            assertApiError(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_PHOTO,
                    () -> photoStorageService.upload(PhotoOwner.BUS, OWNER_ID, file));

            verifyNoInteractions(s3Client);
        }

        @Test
        void s3Failure_throwsStorageUnavailable() {
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenThrow(SdkClientException.create("S3 no responde"));
            MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", CONTENT);

            assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.STORAGE_UNAVAILABLE,
                    () -> photoStorageService.upload(PhotoOwner.BUS, OWNER_ID, file));
        }
    }

    @Nested
    class Delete {

        @Test
        void existingKey_deletesObjectFromBucket() {
            photoStorageService.delete(KEY);

            verify(s3Client).deleteObject(deleteCaptor.capture());
            assertEquals(BUCKET, deleteCaptor.getValue().bucket());
            assertEquals(KEY, deleteCaptor.getValue().key());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void blankKey_doesNothing(String key) {
            photoStorageService.delete(key);

            verifyNoInteractions(s3Client);
        }

        @Test
        void s3Failure_isSwallowed() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenThrow(SdkClientException.create("S3 no responde"));

            assertDoesNotThrow(() -> photoStorageService.delete(KEY));
        }
    }

    @Nested
    class PresignedUrl {

        @Test
        void existingKey_returnsSignedUrlWithConfiguredTtl() throws Exception {
            PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
            when(presigned.url()).thenReturn(URI.create("https://bucket-test.s3.amazonaws.com/signed").toURL());
            when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

            String url = photoStorageService.presignedUrl(KEY);

            assertEquals("https://bucket-test.s3.amazonaws.com/signed", url);
            verify(s3Presigner).presignGetObject(presignCaptor.capture());
            GetObjectPresignRequest request = presignCaptor.getValue();
            assertEquals(TTL, request.signatureDuration());
            assertEquals(BUCKET, request.getObjectRequest().bucket());
            assertEquals(KEY, request.getObjectRequest().key());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void blankKey_returnsNull(String key) {
            assertNull(photoStorageService.presignedUrl(key));

            verifyNoInteractions(s3Presigner);
        }
    }

    private static void assertApiError(HttpStatus status, String code, Executable action) {
        ApiException exception = assertThrows(ApiException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }
}
