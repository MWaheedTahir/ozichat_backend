package com.ozichat.story.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Records a single view of a story by a user.
 * The unique compound index on (storyId, viewerId) prevents duplicate views.
 */
@Document(collection = "story_views")
@CompoundIndexes({
    // Unique: one view record per user per story
    @CompoundIndex(name = "uk_story_view_story_viewer",
            def = "{'storyId': 1, 'viewerId': 1}", unique = true),
    // Fetch all viewers for a story (owner-only endpoint)
    @CompoundIndex(name = "idx_story_views_story",
            def = "{'storyId': 1, 'viewedAt': -1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryView {

    @Id
    private String id;

    @Field("storyId")
    private String storyId;

    @Field("viewerId")
    private Long viewerId;

    @Field("viewedAt")
    private Instant viewedAt;
}
