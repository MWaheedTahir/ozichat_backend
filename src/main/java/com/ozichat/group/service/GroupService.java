package com.ozichat.group.service;

import com.ozichat.common.PagedResponse;
import com.ozichat.conversation.dto.response.MemberResponse;
import com.ozichat.conversation.dto.response.PinnedMessageResponse;
import com.ozichat.group.dto.request.CreateGroupRequest;
import com.ozichat.group.dto.request.SetAnnouncementRequest;
import com.ozichat.group.dto.request.UpdateGroupRequest;
import com.ozichat.group.dto.response.GroupResponse;

import java.util.List;

public interface GroupService {
    GroupResponse createGroup(Long creatorId, CreateGroupRequest request);
    GroupResponse getGroup(Long conversationId, Long requesterId);
    GroupResponse updateGroup(Long conversationId, Long requesterId, UpdateGroupRequest request);
    void addMembers(Long conversationId, Long requesterId, List<Long> userIds);
    void removeMember(Long conversationId, Long requesterId, Long targetUserId);
    void promoteMember(Long conversationId, Long requesterId, Long targetUserId, String role);
    String generateInviteLink(Long conversationId, Long requesterId);
    GroupResponse joinViaInviteLink(Long userId, String token);
    void revokeInviteLink(Long conversationId, Long requesterId);

    // ── Sprint 3 additions ──────────────────────────

    /** Set or clear the group announcement banner. Admins/Owners only. */
    GroupResponse setAnnouncement(Long conversationId, Long requesterId, SetAnnouncementRequest request);

    /** Pin a message. Admins/Owners only. Max 5 pinned messages per conversation. */
    PinnedMessageResponse pinMessage(Long conversationId, Long requesterId, String messageId);

    /** Unpin a message. Admins/Owners only. */
    void unpinMessage(Long conversationId, Long requesterId, String messageId);

    /** Get all pinned messages for a conversation. */
    List<PinnedMessageResponse> getPinnedMessages(Long conversationId, Long requesterId);

    /** Paginated member list. */
    PagedResponse<MemberResponse> getMembers(Long conversationId, Long requesterId, int page, int size);
}
