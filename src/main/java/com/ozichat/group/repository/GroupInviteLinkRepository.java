package com.ozichat.group.repository;

import com.ozichat.group.entity.GroupInviteLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GroupInviteLinkRepository extends JpaRepository<GroupInviteLink, Long> {

    Optional<GroupInviteLink> findByTokenAndIsRevokedFalse(String token);

    Optional<GroupInviteLink> findByConversationIdAndIsRevokedFalse(Long conversationId);
}
