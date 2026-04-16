package com.ozichat.media.service.impl;

import com.ozichat.exception.BusinessException;
import com.ozichat.media.dto.response.MediaUploadResponse;
import com.ozichat.media.dto.response.PresignedUrlResponse;
import com.ozichat.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3MediaServiceImpl implements MediaService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name:ozichat-media}")
    private String bucketName;

    @Value("${aws.s3.region:us-east-1}")
    private String region;

    @Value("${aws.s3.cdn-url:}")
    private String cdnUrl;

    @Value("${aws.s3.presigned-url-expiry-minutes:15}")
    private int presignedUrlExpiryMinutes;

    // 50 MB hard limit
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/quicktime", "video/webm", "video/3gpp",
            "audio/mpeg", "audio/ogg", "audio/wav", "audio/aac",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/zip"
    );

    @Override
    public MediaUploadResponse upload(MultipartFile file, Long userId, String folder) {
        validateFile(file);

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "file");
        String extension = getExtension(originalName);
        String key = buildKey(folder, userId, extension);

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .metadata(java.util.Map.of(
                            "uploadedBy", String.valueOf(userId),
                            "originalName", originalName))
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            String publicUrl = buildPublicUrl(key);
            log.info("File uploaded to S3 — key={} size={} userId={}", key, file.getSize(), userId);

            return MediaUploadResponse.builder()
                    .s3Key(key)
                    .url(publicUrl)
                    .mimeType(file.getContentType())
                    .fileSize(file.getSize())
                    .fileName(originalName)
                    .build();

        } catch (IOException e) {
            log.error("Failed to upload file to S3 — key={}: {}", key, e.getMessage(), e);
            throw new BusinessException("File upload failed. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public PresignedUrlResponse generatePresignedPutUrl(String fileName, Long userId, String folder) {
        String extension = getExtension(fileName);
        String key = buildKey(folder, userId, extension);
        String mimeType = guessMimeType(extension);

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiryMinutes))
                .putObjectRequest(r -> r
                        .bucket(bucketName)
                        .key(key)
                        .contentType(mimeType))
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(presignedUrlExpiryMinutes));

        log.info("Pre-signed PUT URL generated — key={} expiresAt={}", key, expiresAt);

        return PresignedUrlResponse.builder()
                .uploadUrl(presigned.url().toString())
                .s3Key(key)
                .publicUrl(buildPublicUrl(key))
                .expiresAt(expiresAt)
                .build();
    }

    @Override
    public void delete(String s3Key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build());
            log.info("Deleted S3 object — key={}", s3Key);
        } catch (Exception e) {
            log.warn("Failed to delete S3 object — key={}: {}", s3Key, e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File must not be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("File size exceeds the 50 MB limit");
        }
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME_TYPES.contains(mime.toLowerCase())) {
            throw new BusinessException("File type '" + mime + "' is not allowed");
        }
    }

    private String buildKey(String folder, Long userId, String extension) {
        return "%s/%d/%s%s".formatted(
                folder,
                userId,
                UUID.randomUUID(),
                extension.isBlank() ? "" : "." + extension);
    }

    private String buildPublicUrl(String key) {
        if (cdnUrl != null && !cdnUrl.isBlank()) {
            return cdnUrl.stripTrailing() + "/" + key;
        }
        return "https://%s.s3.%s.amazonaws.com/%s".formatted(bucketName, region, key);
    }

    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private String guessMimeType(String extension) {
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png"         -> "image/png";
            case "gif"         -> "image/gif";
            case "webp"        -> "image/webp";
            case "mp4"         -> "video/mp4";
            case "mov"         -> "video/quicktime";
            case "mp3"         -> "audio/mpeg";
            case "ogg"         -> "audio/ogg";
            case "wav"         -> "audio/wav";
            case "pdf"         -> "application/pdf";
            case "doc"         -> "application/msword";
            case "docx"        -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls"         -> "application/vnd.ms-excel";
            case "xlsx"        -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default            -> "application/octet-stream";
        };
    }
}






