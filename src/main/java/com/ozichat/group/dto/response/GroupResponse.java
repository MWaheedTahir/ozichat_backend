package com.ozichat.group.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupResponse {
    private Long conversationId;
    private String groupName;
    private String groupDescription;
    private String groupAvatarUrl;
    private Integer maxMembers;
    private Integer currentMemberCount;
    private Boolean onlyAdminsCanSend;
    private Boolean onlyAdminsCanEditInfo;
    private String announcementText;
    private Instant announcementAt;
    private Long announcementBy;
    private Long createdBy;
    private List<MemberInfo> members;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    public static class MemberInfo {
        private Long userId;
        private String displayName;
        private String avatarUrl;
        private String role;
        private Instant joinedAt;
    }
}
