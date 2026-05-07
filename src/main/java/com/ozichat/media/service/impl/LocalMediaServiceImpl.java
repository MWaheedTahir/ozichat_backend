package com.ozichat.media.service.impl;

import com.ozichat.exception.BusinessException;
import com.ozichat.media.dto.response.MediaUploadResponse;
import com.ozichat.media.dto.response.PresignedUrlResponse;
import com.ozichat.media.service.MediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@Profile({"dev", "local-storage"})
@Slf4j
public class LocalMediaServiceImpl implements MediaService {

    @Value("${media.local.upload-dir:uploads}")
    private String uploadDir;

    @Value("${media.local.base-url:http://localhost:8080/media}")
    private String baseUrl;

    @Value("${media.presigned-url-expiry-minutes:15}")
    private int presignedUrlExpiryMinutes;

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
            Path targetPath = resolveAndCreateDirs(key);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String publicUrl = buildPublicUrl(key);
            log.info("File saved locally — key={} size={} userId={}", key, file.getSize(), userId);

            return MediaUploadResponse.builder()
                    .s3Key(key)
                    .url(publicUrl)
                    .mimeType(file.getContentType())
                    .fileSize(file.getSize())
                    .fileName(originalName)
                    .build();

        } catch (IOException e) {
            log.error("Failed to save file locally — key={}: {}", key, e.getMessage(), e);
            throw new BusinessException("File upload failed. Please try again.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public PresignedUrlResponse generatePresignedPutUrl(String fileName, Long userId, String folder) {
        String extension = getExtension(fileName);
        String key = buildKey(folder, userId, extension);
        Instant expiresAt = Instant.now().plusSeconds(presignedUrlExpiryMinutes * 60L);

        // For local dev, return a plain upload URL instead of a real pre-signed URL
        String uploadUrl = baseUrl + "/upload?key=" + key;

        log.info("Local pseudo-presigned URL generated — key={} expiresAt={}", key, expiresAt);

        return PresignedUrlResponse.builder()
                .uploadUrl(uploadUrl)
                .s3Key(key)
                .publicUrl(buildPublicUrl(key))
                .expiresAt(expiresAt)
                .build();
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(storageKey).normalize();
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("Deleted local file — key={}", storageKey);
            } else {
                log.warn("File not found for deletion — key={}", storageKey);
            }
        } catch (IOException e) {
            log.warn("Failed to delete local file — key={}: {}", storageKey, e.getMessage());
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

    private Path resolveAndCreateDirs(String key) throws IOException {
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = base.resolve(key).normalize();

        // Path traversal guard
        if (!target.startsWith(base)) {
            throw new BusinessException("Invalid file path detected", HttpStatus.BAD_REQUEST);
        }

        Files.createDirectories(target.getParent());
        return target;
    }

    private String buildPublicUrl(String key) {
        return baseUrl.stripTrailing() + "/" + key;
    }

    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}