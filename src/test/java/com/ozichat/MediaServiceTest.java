package com.ozichat;

import com.ozichat.exception.BusinessException;
import com.ozichat.media.dto.response.MediaUploadResponse;
import com.ozichat.media.service.impl.S3MediaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3MediaService unit tests")
class MediaServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3MediaServiceImpl mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new S3MediaServiceImpl(s3Client, s3Presigner);
        ReflectionTestUtils.setField(mediaService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(mediaService, "region", "us-east-1");
        ReflectionTestUtils.setField(mediaService, "cdnUrl", "");
        ReflectionTestUtils.setField(mediaService, "presignedUrlExpiryMinutes", 15);
    }

    @Test
    @DisplayName("upload: success for valid JPEG image")
    void upload_validJpeg_returnsMediaResponse() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[1024]
        );
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());

        MediaUploadResponse response = mediaService.upload(file, 1L, "chat");

        assertThat(response.getUrl()).contains("test-bucket");
        assertThat(response.getMimeType()).isEqualTo("image/jpeg");
        assertThat(response.getFileSize()).isEqualTo(1024L);
        assertThat(response.getFileName()).isEqualTo("photo.jpg");
        assertThat(response.getS3Key()).startsWith("chat/1/");
        assertThat(response.getS3Key()).endsWith(".jpg");
    }

    @Test
    @DisplayName("upload: success for valid PDF document")
    void upload_validPdf_returnsMediaResponse() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", new byte[2048]
        );
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());

        MediaUploadResponse response = mediaService.upload(file, 42L, "chat");

        assertThat(response.getMimeType()).isEqualTo("application/pdf");
        assertThat(response.getS3Key()).startsWith("chat/42/");
    }

    @Test
    @DisplayName("upload: throws BusinessException for disallowed mime type")
    void upload_disallowedMimeType_throwsBusinessException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.exe", "application/x-msdownload", new byte[100]
        );

        assertThatThrownBy(() -> mediaService.upload(file, 1L, "chat"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    @DisplayName("upload: throws BusinessException when file is empty")
    void upload_emptyFile_throwsBusinessException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]
        );

        assertThatThrownBy(() -> mediaService.upload(file, 1L, "chat"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("upload: throws BusinessException when file exceeds 50 MB")
    void upload_fileTooLarge_throwsBusinessException() {
        byte[] largeContent = new byte[(int) (50L * 1024 * 1024 + 1)];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.mp4", "video/mp4", largeContent
        );

        assertThatThrownBy(() -> mediaService.upload(file, 1L, "chat"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("50 MB");
    }

    @Test
    @DisplayName("upload: CDN URL is used when configured")
    void upload_withCdnUrl_returnsCdnUrl() {
        ReflectionTestUtils.setField(mediaService, "cdnUrl", "https://cdn.ozichat.com");

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[512]
        );
        given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willReturn(PutObjectResponse.builder().build());

        MediaUploadResponse response = mediaService.upload(file, 1L, "chat");

        assertThat(response.getUrl()).startsWith("https://cdn.ozichat.com/");
    }
}
