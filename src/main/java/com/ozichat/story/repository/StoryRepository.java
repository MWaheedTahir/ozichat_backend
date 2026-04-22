package com.ozichat.story.repository;

import com.ozichat.story.document.Story;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface StoryRepository extends MongoRepository<Story, String> {

    /** All active (not deleted, not expired) stories belonging to a user — owner view. */
    @Query("{ 'userId': ?0, 'isDeleted': false, 'expiresAt': { $gt: ?1 } }")
    List<Story> findActiveByUserId(Long userId, Instant now, Sort sort);

    /**
     * Stories by a target user that a given caller is allowed to see:
     * - PUBLIC stories from that user, OR
     * - PRIVATE stories where callerId is in allowedViewerIds.
     */
    @Query("{ 'userId': ?0, 'isDeleted': false, 'expiresAt': { $gt: ?2 }, " +
           "$or: [ { 'privacy': 'PUBLIC' }, { 'privacy': 'PRIVATE', 'allowedViewerIds': ?1 } ] }")
    List<Story> findVisibleByUserId(Long targetUserId, Long callerId, Instant now, Sort sort);

    /**
     * Feed query — all stories the caller is allowed to see:
     * - All PUBLIC stories, OR
     * - PRIVATE stories where callerId is in allowedViewerIds.
     * Used to build the per-user grouped feed.
     */
    @Query("{ 'isDeleted': false, 'expiresAt': { $gt: ?1 }, " +
           "$or: [ { 'privacy': 'PUBLIC' }, { 'privacy': 'PRIVATE', 'allowedViewerIds': ?0 } ] }")
    List<Story> findFeedStories(Long callerId, Instant now, Sort sort);

    /** Single story — excludes soft-deleted. */
    Optional<Story> findByIdAndIsDeletedFalse(String id);

    /** How many active stories a user currently has. */
    @Query(value = "{ 'userId': ?0, 'isDeleted': false, 'expiresAt': { $gt: ?1 } }",
           count = true)
    long countActiveByUserId(Long userId, Instant now);
}
