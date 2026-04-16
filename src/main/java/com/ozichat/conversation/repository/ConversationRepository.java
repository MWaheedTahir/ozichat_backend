package com.ozichat.conversation.repository;

import com.ozichat.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {


    /**
     * Finds an existing DIRECT conversation shared exactly by these two users.
     * Uses a double-join to ensure both users are members.
     */
    @Query("SELECT c FROM Conversation c " +
           "JOIN c.members m1 ON m1.userId = :userId1 AND m1.leftAt IS NULL " +
           "JOIN c.members m2 ON m2.userId = :userId2 AND m2.leftAt IS NULL " +
           "WHERE c.type = 'DIRECT'")
    Optional<Conversation> findDirectConversation(@Param("userId1") Long userId1,
                                                  @Param("userId2") Long userId2);

    @Query("SELECT DISTINCT c FROM Conversation c " +
           "JOIN c.members m ON m.userId = :userId AND m.leftAt IS NULL " +
           "ORDER BY c.updatedAt DESC")
    List<Conversation> findAllByUserId(@Param("userId") Long userId);
}
