package com.ozichat.user.dto.response;

import com.ozichat.user.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String phone;
    private String email;
    private String displayName;
    private String avatarUrl;
    private String about;
    private String role;
    private Boolean isVerified;
    private Instant lastSeenAt;
    private Instant createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .about(user.getAbout())
                .role(user.getRole().name())
                .isVerified(user.getIsVerified())
                .lastSeenAt(user.getLastSeenAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
