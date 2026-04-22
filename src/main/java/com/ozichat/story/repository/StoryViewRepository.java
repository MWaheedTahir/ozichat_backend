package com.ozichat.story.repository;

import com.ozichat.story.document.StoryView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoryViewRepository extends MongoRepository<StoryView, String> {

    /** True if this viewer has already recorded a view for this story. */
    boolean existsByStoryIdAndViewerId(String storyId, Long viewerId);

    /** Total view count for a story. */
    long countByStoryId(String storyId);

    /** Paginated list of viewers — newest first. Owner-only endpoint. */
    List<StoryView> findByStoryIdOrderByViewedAtDesc(String storyId, Pageable pageable);
}
