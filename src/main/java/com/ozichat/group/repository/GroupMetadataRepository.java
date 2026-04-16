package com.ozichat.group.repository;

import com.ozichat.conversation.entity.Conversation;
import com.ozichat.group.entity.GroupMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupMetadataRepository extends JpaRepository<GroupMetadata, Long> {
    Optional<GroupMetadata> findByConversationId(Long conversationId);
}
