package com.ozichat.conversation.repository;

import com.ozichat.conversation.entity.PinnedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PinnedMessageRepository extends JpaRepository<PinnedMessage, Long> {

    List<PinnedMessage> findByConversationIdOrderByPinnedAtDesc(Long conversationId);

    Optional<PinnedMessage> findByConversationIdAndMessageId(Long conversationId, String messageId);

    boolean existsByConversationIdAndMessageId(Long conversationId, String messageId);

    long countByConversationId(Long conversationId);

    void deleteByConversationIdAndMessageId(Long conversationId, String messageId);
}
