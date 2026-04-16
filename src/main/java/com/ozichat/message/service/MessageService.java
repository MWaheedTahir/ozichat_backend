package com.ozichat.message.service;

import com.ozichat.common.CursorPagedResponse;
import com.ozichat.message.document.Message;
import com.ozichat.message.dto.request.SendMessageRequest;
import com.ozichat.message.dto.response.MessageResponse;

import java.time.Instant;
import java.util.List;

public interface MessageService {
    Message saveMessage(Long conversationId, Long senderId, SendMessageRequest request);
    CursorPagedResponse<MessageResponse> getMessageHistory(Long conversationId, Long userId,
                                                           String cursor, int limit, String direction);
    List<MessageResponse> getMissedMessages(Long userId, Instant since);
    MessageResponse editMessage(String messageId, Long userId, String newContent);
    void deleteMessage(String messageId, Long userId, String scope);
}
