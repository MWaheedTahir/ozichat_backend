package com.ozichat.message.repository;

import com.ozichat.message.document.Message;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    List<Message> findByConversationIdAndIsDeletedForEveryoneFalse(Long conversationId, Sort sort);

    @Query("{ 'conversationId': ?0, '_id': { $lt: ?1 }, 'isDeletedForEveryone': false }")
    List<Message> findBeforeCursor(Long conversationId, org.bson.types.ObjectId cursorId, Sort sort);

    @Query("{ 'conversationId': ?0, '_id': { $gt: ?1 }, 'isDeletedForEveryone': false }")
    List<Message> findAfterCursor(Long conversationId, org.bson.types.ObjectId cursorId, Sort sort);

    @Query("{ 'conversationId': { $in: ?0 }, 'createdAt': { $gt: ?1 }, 'isDeletedForEveryone': false }")
    List<Message> findMissedMessages(List<Long> conversationIds, Instant since, Sort sort);

    long countByConversationIdAndCreatedAtAfterAndSenderIdNot(
            Long conversationId, Instant after, Long excludeSenderId);
}
