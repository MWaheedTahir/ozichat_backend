package com.ozichat.reel.repository;

import com.ozichat.reel.document.ReelComment;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReelCommentRepository extends MongoRepository<ReelComment, String> {

    /**
     * Comments for a reel, newest first, cursor-based.
     * Fetches comments with _id < cursor (older than the cursor).
     */
    @Query("{ 'reelId': ?0, 'isDeleted': false, '_id': { $lt: ?1 } }")
    List<ReelComment> findByReelIdBeforeCursor(String reelId, ObjectId cursor,
                                               org.springframework.data.domain.Pageable pageable);

    /**
     * Initial comments page — no cursor.
     */
    @Query("{ 'reelId': ?0, 'isDeleted': false }")
    List<ReelComment> findByReelIdInitial(String reelId,
                                          org.springframework.data.domain.Pageable pageable);

    Optional<ReelComment> findByIdAndIsDeletedFalse(String id);

    long countByReelIdAndIsDeletedFalse(String reelId);
}
