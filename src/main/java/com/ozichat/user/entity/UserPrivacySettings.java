package com.ozichat.user.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_privacy_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPrivacySettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_seen_visibility")
    @Builder.Default
    private Visibility lastSeenVisibility = Visibility.EVERYONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_photo_visibility")
    @Builder.Default
    private Visibility profilePhotoVisibility = Visibility.EVERYONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "about_visibility")
    @Builder.Default
    private Visibility aboutVisibility = Visibility.EVERYONE;

    @Column(name = "read_receipts_enabled")
    @Builder.Default
    private Boolean readReceiptsEnabled = true;

    public enum Visibility {
        EVERYONE, CONTACTS, NOBODY
    }
}
