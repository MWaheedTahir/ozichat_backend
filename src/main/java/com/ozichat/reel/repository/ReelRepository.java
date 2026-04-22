package com.ozichat.reel.repository;

import com.ozichat.reel.document.Reel;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReelRepository extends MongoRepository<Reel, String> {

    /**
     * Global feed — newest first, cursor-based (fetch limit+1 to determine hasMore).
     * Only returns non-deleted reels with _id < cursor (i.e., older than cursor).
     * Sort is embedded in the Pageable (PageRequest.of(0, n, Sort.by(DESC, "_id"))).
     */
    @Query("{ 'isDeleted': false, '_id': { $lt: ?0 } }")
    List<Reel> findFeedBeforeCursor(ObjectId cursor, Pageable pageable);

    /**
     * Initial feed page — no cursor, just most recent.
     */
    @Query("{ 'isDeleted': false }")
    List<Reel> findFeed(Pageable pageable);

    /**
     * Reels by a specific user, newest first, cursor-based.
     */
    @Query("{ 'userId': ?0, 'isDeleted': false, '_id': { $lt: ?1 } }")
    List<Reel> findByUserIdBeforeCursor(Long userId, ObjectId cursor, Pageable pageable);

    /**
     * Reels by a specific user — initial page (no cursor).
     */
    @Query("{ 'userId': ?0, 'isDeleted': false }")
    List<Reel> findByUserIdInitial(Long userId, Pageable pageable);

    /**
     * Single reel — only non-deleted.
     */
    Optional<Reel> findByIdAndIsDeletedFalse(String id);

    /**
     * Reels by hashtag — newest first, pageable.
     */
    @Query("{ 'hashtags': ?0, 'isDeleted': false }")
    List<Reel> findByHashtag(String hashtag, Pageable pageable);

    long countByUserIdAndIsDeletedFalse(Long userId);
}
