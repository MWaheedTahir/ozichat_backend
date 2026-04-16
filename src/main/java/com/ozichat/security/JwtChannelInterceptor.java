package com.ozichat.security;

import com.ozichat.conversation.repository.ConversationMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final ConversationMemberRepository conversationMemberRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("WebSocket CONNECT rejected: missing or invalid Authorization header");
                throw new IllegalArgumentException("Authorization header missing");
            }

            String token = authHeader.substring(7);
            if (!jwtTokenProvider.isTokenValid(token) || !jwtTokenProvider.isAccessToken(token)) {
                log.warn("WebSocket CONNECT rejected: invalid token");
                throw new IllegalArgumentException("Invalid or expired token");
            }

            Long userId = jwtTokenProvider.extractUserId(token);
            accessor.setUser(new StompPrincipal(userId.toString()));
            log.info("WebSocket CONNECT authenticated for userId={}", userId);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/topic/conversation/")) {
                if (accessor.getUser() == null) {
                    throw new IllegalArgumentException("Not authenticated");
                }
                Long userId = Long.parseLong(accessor.getUser().getName());
                String convIdStr = destination.replace("/topic/conversation/", "");
                try {
                    Long conversationId = Long.parseLong(convIdStr);
                    boolean isMember = conversationMemberRepository
                            .existsByConversationIdAndUserIdAndLeftAtIsNull(conversationId, userId);
                    if (!isMember) {
                        log.warn("SUBSCRIBE rejected: user {} is not member of conversation {}", userId, conversationId);
                        throw new IllegalArgumentException("Not a member of this conversation");
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid conversation id in subscription destination");
                }
            }
        }

        return message;
    }
}
