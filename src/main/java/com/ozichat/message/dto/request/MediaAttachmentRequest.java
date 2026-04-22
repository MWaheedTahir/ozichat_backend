package com.ozichat.message.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Carries the S3 metadata for a voice note, video, image, or document message.
 *
 * Flow:
 *  1. Client calls POST /api/v1/media/presign to get a presigned PUT URL + s3Key + publicUrl
 *  2. Client uploads the binary directly to S3 using the PUT URL
 *  3. Client sends this DTO (inside SendMessageRequest.media) over WebSocket or REST
 */
@Data
public class MediaAttachmentRequest {

    /** S3 object key — returned by the presign endpoint. Used for deletion. */
    @NotBlank(message = "s3Key is required")
    private String s3Key;

    /** Public CDN / S3 URL that recipients use to download the file. */
    @NotBlank(message = "url is required")
    private String url;

    /** Optional thumbnail URL (for video and image messages). */
    private String thumbnailUrl;

    /** MIME type — e.g. "audio/mp4", "video/mp4", "image/jpeg", "application/pdf". */
    @NotBlank(message = "mimeType is required")
    private String mimeType;

    /** File size in bytes. */
    @Positive(message = "fileSize must be positive")
    private Long fileSize;

    /** Original filename shown in the UI. */
    private String fileName;

    /**
     * Duration in seconds — required for AUDIO and VIDEO messages.
     * Clients must send this so the chat UI can display a progress bar.
     */
    private Integer duration;

    /** Pixel width — for IMAGE and VIDEO messages. */
    private Integer width;

    /** Pixel height — for IMAGE and VIDEO messages. */
    private Integer height;
}
