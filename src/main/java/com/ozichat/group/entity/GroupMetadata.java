package com.ozichat.group.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "groups_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMetadata {

    @Id
    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "group_name", nullable = false, length = 100)
    private String groupName;

    @Column(name = "group_description", length = 500)
    private String groupDescription;

    @Column(name = "group_avatar_url", length = 500)
    private String groupAvatarUrl;

    @Column(name = "max_members")
    @Builder.Default
    private Integer maxMembers = 1024;

    @Column(name = "only_admins_can_send")
    @Builder.Default
    private Boolean onlyAdminsCanSend = false;

    @Column(name = "only_admins_can_edit_info")
    @Builder.Default
    private Boolean onlyAdminsCanEditInfo = true;

    /** Optional group-wide announcement banner (admin/owner only) */
    @Column(name = "announcement_text", length = 1000)
    private String announcementText;

    @Column(name = "announcement_at")
    private Instant announcementAt;

    @Column(name = "announcement_by")
    private Long announcementBy;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
