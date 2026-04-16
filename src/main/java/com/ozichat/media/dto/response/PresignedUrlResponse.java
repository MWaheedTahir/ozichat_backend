package com.ozichat.media.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class PresignedUrlResponse {
    /** Pre-signed S3 URL — the client should HTTP PUT the file directly here */
    private String uploadUrl;
    /** S3 object key — store this alongside the message so you can build the final URL */
    private String s3Key;
    /** Public/CDN URL of the object once the upload is complete */
    private String publicUrl;
    /** When the pre-signed URL expires */
    private Instant expiresAt;
}
