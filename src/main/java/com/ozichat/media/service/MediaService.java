package com.ozichat.media.service;

import com.ozichat.media.dto.response.MediaUploadResponse;
import com.ozichat.media.dto.response.PresignedUrlResponse;
import org.springframework.web.multipart.MultipartFile;

public interface MediaService {

    /**
     * Upload a file directly (server-side upload).
     * Validates type and size, stores in S3, returns public/CDN URL.
     *
     * @param file     multipart file from the request
     * @param userId   uploader's user ID (used to namespace keys)
     * @param folder   e.g. "avatars", "chat", "groups"
     */
    MediaUploadResponse upload(MultipartFile file, Long userId, String folder);

    /**
     * Generate a pre-signed PUT URL so the client can upload directly to S3
     * without routing through the backend (reduces server load for large files).
     *
     * @param fileName  desired file name (used to derive content-type hint)
     * @param userId    uploader's user ID
     * @param folder    S3 key prefix
     */
    PresignedUrlResponse generatePresignedPutUrl(String fileName, Long userId, String folder);

    /**
     * Delete an object by its S3 key.
     */
    void delete(String s3Key);
}
