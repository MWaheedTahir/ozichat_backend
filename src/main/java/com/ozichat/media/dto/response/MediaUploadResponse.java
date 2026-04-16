package com.ozichat.media.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MediaUploadResponse {
    private String s3Key;
    private String url;
    private String mimeType;
    private Long fileSize;
    private String fileName;
    private Integer width;
    private Integer height;
}
