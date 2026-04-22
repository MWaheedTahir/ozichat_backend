package com.ozichat.story.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ozichat.story.document.StoryView;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoryViewerResponse {

    private Long viewerId;
    private String viewerName;
    private String viewerAvatarUrl;
    private Instant viewedAt;

    public static StoryViewerResponse from(StoryView view,
                                           String viewerName,
                                           String viewerAvatarUrl) {
        return StoryViewerResponse.builder()
                .viewerId(view.getViewerId())
                .viewerName(viewerName)
                .viewerAvatarUrl(viewerAvatarUrl)
                .viewedAt(view.getViewedAt())
                .build();
    }
}
