package com.ozichat.group.controller;

import com.ozichat.common.ApiResponse;
import com.ozichat.common.PagedResponse;
import com.ozichat.conversation.dto.response.MemberResponse;
import com.ozichat.conversation.dto.response.PinnedMessageResponse;
import com.ozichat.group.dto.request.CreateGroupRequest;
import com.ozichat.group.dto.request.SetAnnouncementRequest;
import com.ozichat.group.dto.request.UpdateGroupRequest;
import com.ozichat.group.dto.response.GroupResponse;
import com.ozichat.group.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
@Tag(name = "Groups", description = "Group chat management")
@SecurityRequirement(name = "bearerAuth")
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    @Operation(summary = "Create a new group")
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateGroupRequest request) {
        GroupResponse group = groupService.createGroup(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Group created", group));
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Get group info")
    public ResponseEntity<ApiResponse<GroupResponse>> getGroup(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId) {
        return ResponseEntity.ok(ApiResponse.success(groupService.getGroup(conversationId, userId)));
    }

    @PatchMapping("/{conversationId}")
    @Operation(summary = "Update group info (admin or owner only)")
    public ResponseEntity<ApiResponse<GroupResponse>> updateGroup(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @Valid @RequestBody UpdateGroupRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Group updated",
                groupService.updateGroup(conversationId, userId, request)));
    }

    // ── Members ────────────────────────────────────

    @GetMapping("/{conversationId}/members")
    @Operation(summary = "Get paginated member list")
    public ResponseEntity<ApiResponse<PagedResponse<MemberResponse>>> getMembers(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                groupService.getMembers(conversationId, userId, page, size)));
    }

    @PostMapping("/{conversationId}/members")
    @Operation(summary = "Add members to the group (admin or owner only)")
    public ResponseEntity<ApiResponse<Void>> addMembers(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @RequestBody Map<String, List<Long>> body) {
        groupService.addMembers(conversationId, userId, body.get("memberIds"));
        return ResponseEntity.ok(ApiResponse.success("Members added"));
    }

    @DeleteMapping("/{conversationId}/members/{targetUserId}")
    @Operation(summary = "Remove a member or leave the group")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @PathVariable Long targetUserId) {
        groupService.removeMember(conversationId, userId, targetUserId);
        return ResponseEntity.ok(ApiResponse.success("Member removed"));
    }

    @PatchMapping("/{conversationId}/members/{targetUserId}/role")
    @Operation(summary = "Promote or demote a member (owner only)")
    public ResponseEntity<ApiResponse<Void>> updateMemberRole(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @PathVariable Long targetUserId,
            @RequestBody Map<String, String> body) {
        groupService.promoteMember(conversationId, userId, targetUserId, body.get("role"));
        return ResponseEntity.ok(ApiResponse.success("Role updated"));
    }

    // ── Announcement ──────────────────────────────

    @PutMapping("/{conversationId}/announcement")
    @Operation(summary = "Set or clear the group announcement (admin or owner only)")
    public ResponseEntity<ApiResponse<GroupResponse>> setAnnouncement(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @Valid @RequestBody SetAnnouncementRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Announcement updated",
                groupService.setAnnouncement(conversationId, userId, request)));
    }

    // ── Pinned Messages ───────────────────────────

    @GetMapping("/{conversationId}/pinned")
    @Operation(summary = "Get all pinned messages in the group")
    public ResponseEntity<ApiResponse<List<PinnedMessageResponse>>> getPinnedMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId) {
        return ResponseEntity.ok(ApiResponse.success(
                groupService.getPinnedMessages(conversationId, userId)));
    }

    @PostMapping("/{conversationId}/pinned/{messageId}")
    @Operation(summary = "Pin a message (admin or owner only, max 5 pinned)")
    public ResponseEntity<ApiResponse<PinnedMessageResponse>> pinMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @PathVariable String messageId) {
        PinnedMessageResponse pinned = groupService.pinMessage(conversationId, userId, messageId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message pinned", pinned));
    }

    @DeleteMapping("/{conversationId}/pinned/{messageId}")
    @Operation(summary = "Unpin a message (admin or owner only)")
    public ResponseEntity<ApiResponse<Void>> unpinMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId,
            @PathVariable String messageId) {
        groupService.unpinMessage(conversationId, userId, messageId);
        return ResponseEntity.ok(ApiResponse.success("Message unpinned"));
    }

    // ── Invite Links ──────────────────────────────

    @PostMapping("/{conversationId}/invite-link")
    @Operation(summary = "Generate an invite link (admin or owner only)")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateInviteLink(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId) {
        String token = groupService.generateInviteLink(conversationId, userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("token", token, "inviteLink", "/join/" + token)));
    }

    @DeleteMapping("/{conversationId}/invite-link")
    @Operation(summary = "Revoke the active invite link (admin or owner only)")
    public ResponseEntity<ApiResponse<Void>> revokeInviteLink(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long conversationId) {
        groupService.revokeInviteLink(conversationId, userId);
        return ResponseEntity.ok(ApiResponse.success("Invite link revoked"));
    }

    @PostMapping("/join/{token}")
    @Operation(summary = "Join a group via invite link")
    public ResponseEntity<ApiResponse<GroupResponse>> joinViaLink(
            @AuthenticationPrincipal Long userId,
            @PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success("Joined group",
                groupService.joinViaInviteLink(userId, token)));
    }
}
