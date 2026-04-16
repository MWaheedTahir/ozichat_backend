package com.ozichat.media.controller;

import com.ozichat.common.ApiResponse;
import com.ozichat.media.dto.response.MediaUploadResponse;
import com.ozichat.media.dto.response.PresignedUrlResponse;
import com.ozichat.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "File upload and pre-signed URL endpoints")
public class MediaController {

    private final MediaService mediaService;

    /**
     * Direct server-side upload.
     * Best for small files (avatars, thumbnails).
     * The response URL can be stored directly in the message's media.url field.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file (server-side). Returns public URL.")
    public ResponseEntity<ApiResponse<MediaUploadResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "chat") String folder,
            @AuthenticationPrincipal Long userId) {

        MediaUploadResponse response = mediaService.upload(file, userId, folder);
        return ResponseEntity.ok(ApiResponse.success("File uploaded successfully", response));
    }

    /**
     * Generate a pre-signed PUT URL so the mobile/web client uploads directly to S3.
     * Recommended for large videos/documents — avoids routing through the backend.
     *
     * Flow:
     *   1. Client calls POST /api/v1/media/presign?fileName=video.mp4&folder=chat
     *   2. Server returns { uploadUrl, s3Key, publicUrl }
     *   3. Client HTTP PUT the file binary to uploadUrl (no auth header needed)
     *   4. Client includes s3Key / publicUrl in the SendMessageRequest.media fields
     */
    @PostMapping("/presign")
    @Operation(summary = "Get a pre-signed PUT URL for direct-to-S3 upload")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> presign(
            @Parameter(description = "Original file name (used to infer content-type)") @RequestParam String fileName,
            @RequestParam(value = "folder", defaultValue = "chat") String folder,
            @AuthenticationPrincipal Long userId) {

        PresignedUrlResponse response = mediaService.generatePresignedPutUrl(fileName, userId, folder);
        return ResponseEntity.ok(ApiResponse.success("Pre-signed URL generated", response));
    }

    /**
     * Delete a media file by its S3 key.
     * Should only be called when the associated message is deleted FOR_EVERYONE.
     */
    @DeleteMapping
    @Operation(summary = "Delete a media object from S3 by its key")
    public ResponseEntity<ApiResponse<Void>> delete(
            @RequestParam String s3Key,
            @AuthenticationPrincipal Long userId) {

        // Note: In production, verify userId owns the file before deleting
        mediaService.delete(s3Key);
        return ResponseEntity.ok(ApiResponse.success("File deleted"));
    }
}
