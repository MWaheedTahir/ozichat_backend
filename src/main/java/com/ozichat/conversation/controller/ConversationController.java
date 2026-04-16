package com.ozichat.conversation.controller;

import com.ozichat.common.ApiResponse;
import com.ozichat.conversation.dto.response.ConversationListResponse;
import com.ozichat.conversation.dto.response.ConversationResponse;
import com.ozichat.conversation.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversations", description = "Conversation management")
@SecurityRequirement(name = "bearerAuth")
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping("/direct")
    @Operation(summary = "Get or create a direct conversation with another user")
    public ResponseEntity<ApiResponse<ConversationResponse>> getOrCreateDirect(
            @AuthenticationPrincipal Long userId,
            @RequestParam Long targetUserId) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getOrCreateDirect(userId, targetUserId)));
    }

    @GetMapping
    @Operation(summary = "List all conversations for the authenticated user")
    public ResponseEntity<ApiResponse<List<ConversationListResponse>>> getConversations(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getUserConversations(userId)));
    }

//    @GetMapping
//    @Operation(summary = "List all conversations for the authenticated user")
//    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getConversations(
//            @AuthenticationPrincipal Long userId) {
//        return ResponseEntity.ok(ApiResponse.success(
//                conversationService.getUserConversations(userId)));
//    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific conversation by ID")
    public ResponseEntity<ApiResponse<ConversationResponse>> getConversation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getConversation(id, userId)));
    }
}
