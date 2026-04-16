package com.ozichat.conversation.repository;

import com.ozichat.conversation.entity.ConversationMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    boolean existsByConversationIdAndUserIdAndLeftAtIsNull(Long conversationId, Long userId);

    Optional<ConversationMember> findByConversationIdAndUserIdAndLeftAtIsNull(Long conversationId, Long userId);

    List<ConversationMember> findByConversationIdAndLeftAtIsNull(Long conversationId);
    List<ConversationMember> findByConversationIdAndLeftAtIsNullAndUserIdNot(Long conversationId, Long userId);

    /** Paginated member list — used by GET /groups/{id}/members */
    Page<ConversationMember> findByConversationIdAndLeftAtIsNull(Long conversationId, Pageable pageable);

    @Query("SELECT m.userId FROM ConversationMember m " +
           "WHERE m.conversation.id = :conversationId AND m.userId <> :excludeUserId AND m.leftAt IS NULL")
    List<Long> findOtherMemberIds(@Param("conversationId") Long conversationId,
                                  @Param("excludeUserId") Long excludeUserId);

    @Query("SELECT m.conversation.id FROM ConversationMember m " +
           "WHERE m.userId = :userId AND m.leftAt IS NULL")
    List<Long> findConversationIdsByUserId(@Param("userId") Long userId);
}
