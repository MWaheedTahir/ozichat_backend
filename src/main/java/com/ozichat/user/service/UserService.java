package com.ozichat.user.service;

import com.ozichat.common.PagedResponse;
import com.ozichat.user.dto.request.UpdateProfileRequest;
import com.ozichat.user.dto.response.UserResponse;
import com.ozichat.user.entity.User;

import java.time.Instant;

public interface UserService {
    UserResponse getProfile(Long userId);
    UserResponse getPublicProfile(Long requesterId, Long targetId);
    UserResponse updateProfile(Long userId, UpdateProfileRequest request);
    void deleteAccount(Long userId);
    void updateLastSeen(Long userId);
    PagedResponse<UserResponse> searchUsers(String query, int page, int size);
    User getActiveUserOrThrow(Long userId);
}
