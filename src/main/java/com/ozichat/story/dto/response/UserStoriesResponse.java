package com.ozichat.story.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Feed entry: one user's active stories, grouped together.
 * Clients render this as a single story ring/bubble in the feed bar.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserStoriesResponse {

    private Long userId;
    private String userName;
    private String userAvatarUrl;

    /**
     * True if the caller has at least one unviewed story from this user.
     * Used to render the colored ring vs grey ring in the UI.
     */
    private boolean hasUnviewed;

    /** The user's active stories, newest first. */
    private List<StoryResponse> stories;
}
