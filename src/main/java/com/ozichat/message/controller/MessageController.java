package com.ozichat.message.controller;

import com.ozichat.common.ApiResponse;
import com.ozichat.common.CursorPagedResponse;
import com.ozichat.message.dto.response.MessageResponse;
import com.ozichat.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "Message history, sync, edit, and delete")
@SecurityRequirement(name = "bearerAuth")
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Get message history (cursor-based pagination)")
    public ResponseEntity<ApiResponse<CursorPagedResponse<MessageResponse>>> getMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "BEFORE") String direction) {

        CursorPagedResponse<MessageResponse> page =
                messageService.getMessageHistory(conversationId, userId, cursor, limit, direction);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/messages/missed")
    @Operation(summary = "Sync missed messages since a given timestamp (call on reconnect)")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMissedMessages(
            @AuthenticationPrincipal Long userId,
            @RequestParam String since) {

        Instant sinceInstant = Instant.parse(since);
        List<MessageResponse> missed = messageService.getMissedMessages(userId, sinceInstant);
        return ResponseEntity.ok(ApiResponse.success(missed));
    }

    @PatchMapping("/messages/{messageId}")
    @Operation(summary = "Edit a message (sender only, within 15-minute window)")
    public ResponseEntity<ApiResponse<?>> editMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable String messageId,
            @RequestBody Map<String, String> body) {

        String newContent = body.get("content");
        if (newContent == null || newContent.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("content is required"));
        }
        MessageResponse updated = messageService.editMessage(messageId, userId, newContent);
        return ResponseEntity.ok(ApiResponse.success("Message updated", updated));
    }

    @DeleteMapping("/messages/{messageId}")
    @Operation(summary = "Delete a message — scope: FOR_ME or FOR_EVERYONE")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable String messageId,
            @RequestParam(defaultValue = "FOR_ME") String scope) {

        messageService.deleteMessage(messageId, userId, scope);
        return ResponseEntity.ok(ApiResponse.success("Message deleted"));
    }
}
