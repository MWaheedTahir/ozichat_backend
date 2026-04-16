package com.ozichat.user.service.impl;

import com.ozichat.common.PagedResponse;
import com.ozichat.exception.ResourceNotFoundException;
import com.ozichat.user.dto.request.UpdateProfileRequest;
import com.ozichat.user.dto.response.UserResponse;
import com.ozichat.user.entity.User;
import com.ozichat.user.repository.UserRepository;
import com.ozichat.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        User user = getActiveUserOrThrow(userId);
        return UserResponse.from(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getPublicProfile(Long requesterId, Long targetId) {
        User user = getActiveUserOrThrow(targetId);
        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getActiveUserOrThrow(userId);

        if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
        if (request.getAbout() != null) user.setAbout(request.getAbout());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());

        user = userRepository.save(user);
        log.info("Profile updated for userId={}", userId);
        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public void deleteAccount(Long userId) {
        User user = getActiveUserOrThrow(userId);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
        log.info("Account soft-deleted for userId={}", userId);
    }

    @Override
    @Transactional
    public void updateLastSeen(Long userId) {
        userRepository.findByIdAndDeletedAtIsNull(userId).ifPresent(user -> {
            user.setLastSeenAt(Instant.now());
            userRepository.save(user);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> searchUsers(String query, int page, int size) {
        Page<User> users = userRepository.searchUsers(query, PageRequest.of(page, size));
        return PagedResponse.<UserResponse>builder()
                .content(users.getContent().stream().map(UserResponse::from).toList())
                .page(page)
                .size(size)
                .totalElements(users.getTotalElements())
                .totalPages(users.getTotalPages())
                .hasNext(users.hasNext())
                .hasPrevious(users.hasPrevious())
                .build();
    }

    @Override
    public User getActiveUserOrThrow(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
