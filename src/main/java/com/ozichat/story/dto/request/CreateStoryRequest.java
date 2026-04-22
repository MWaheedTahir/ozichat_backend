package com.ozichat.story.dto.request;

import com.ozichat.story.document.Story;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class CreateStoryRequest {

    // ── Media (already uploaded to S3 via presign flow) ────────────────────

    @NotBlank(message = "mediaKey is required")
    private String mediaKey;

    @NotBlank(message = "mediaUrl is required")
    private String mediaUrl;

    /** Optional — required for VIDEO stories so the UI can show a poster frame. */
    private String thumbnailKey;
    private String thumbnailUrl;

    @NotNull(message = "type is required (IMAGE or VIDEO)")
    private Story.StoryType type;

    /** Duration in seconds — required for VIDEO stories. */
    @Positive(message = "duration must be a positive number of seconds")
    private Integer duration;

    // ── Content ────────────────────────────────────────────────────────────

    @Size(max = 500, message = "caption must not exceed 500 characters")
    private String caption;

    // ── Privacy ────────────────────────────────────────────────────────────

    @NotNull(message = "privacy is required (PUBLIC or PRIVATE)")
    private Story.StoryPrivacy privacy;

    /**
     * User IDs that can view this story — required when privacy = PRIVATE.
     * Ignored for PUBLIC stories.
     */
    private List<Long> allowedViewerIds;

    // ── Cross-field validation ─────────────────────────────────────────────

    @AssertTrue(message = "allowedViewerIds must not be empty for PRIVATE stories")
    private boolean isAllowedViewersValid() {
        if (privacy != Story.StoryPrivacy.PRIVATE) return true;
        return allowedViewerIds != null && !allowedViewerIds.isEmpty();
    }

    @AssertTrue(message = "duration is required for VIDEO stories")
    private boolean isDurationValid() {
        if (type != Story.StoryType.VIDEO) return true;
        return duration != null && duration > 0;
    }
}
