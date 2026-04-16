package com.ozichat.user.controller;

import com.ozichat.common.ApiResponse;
import com.ozichat.common.PagedResponse;
import com.ozichat.user.dto.request.UpdateProfileRequest;
import com.ozichat.user.dto.response.UserResponse;
import com.ozichat.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and discovery")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get own profile")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getProfile(userId)));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update own profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Profile updated", userService.updateProfile(userId, request)));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Delete own account (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@AuthenticationPrincipal Long userId) {
        userService.deleteAccount(userId);
        return ResponseEntity.ok(ApiResponse.success("Account deleted"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get public profile of a user")
    public ResponseEntity<ApiResponse<UserResponse>> getUserProfile(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getPublicProfile(requesterId, id)));
    }

    @GetMapping("/search")
    @Operation(summary = "Search users by name, email or phone")
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> searchUsers(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(userService.searchUsers(q, page, size)));
    }
}
