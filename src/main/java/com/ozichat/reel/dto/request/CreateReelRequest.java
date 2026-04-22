package com.ozichat.reel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Used when the client uploads video directly to S3 via pre-signed URL first,
 * then calls POST /api/v1/reels to register the reel.
 *
 * Flow:
 *  1. POST /api/v1/media/presign?fileName=clip.mp4&folder=reels  → { uploadUrl, s3Key, publicUrl }
 *  2. Client PUTs the video binary to uploadUrl
 *  3. (Optionally) POST /api/v1/media/presign?fileName=thumb.jpg&folder=reels-thumbs → thumbnail
 *  4. Client PUTs the thumbnail binary
 *  5. POST /api/v1/reels with this request body
 */
@Getter
@Setter
public class CreateReelRequest {

    @NotBlank(message = "Video S3 key is required")
    private String videoKey;

    @NotBlank(message = "Video URL is required")
    private String videoUrl;

    private String thumbnailKey;
    private String thumbnailUrl;

    @Size(max = 2200, message = "Caption must not exceed 2200 characters")
    private String caption;

    @Positive(message = "Duration must be a positive number of seconds")
    private Integer duration;

    private Long fileSize;

    private Integer width;
    private Integer height;

    private String mimeType;
}
