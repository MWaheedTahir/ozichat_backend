package com.ozichat.conversation.service;

import com.ozichat.conversation.dto.response.ConversationListResponse;
import com.ozichat.conversation.dto.response.ConversationResponse;
import com.ozichat.conversation.entity.Conversation;
import com.ozichat.message.document.Message;

import java.util.List;

public interface ConversationService {
    ConversationResponse getOrCreateDirect(Long requesterId, Long targetId);
    List<ConversationListResponse> getUserConversations(Long userId);
    ConversationResponse getConversation(Long conversationId, Long userId);
    boolean isMember(Long conversationId, Long userId);
    List<Long> getOtherMemberIds(Long conversationId, Long userId);
    List<Long> getUserConversationIds(Long userId);
    void updateLastReadMessage(Long conversationId, Long userId, String lastReadMessageId);
}
