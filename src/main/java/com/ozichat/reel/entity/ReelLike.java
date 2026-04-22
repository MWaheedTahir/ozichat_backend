package com.ozichat.reel.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Tracks which users liked which reels.
 *
 * Stored in MySQL (not MongoDB) so that EXISTS checks and COUNT queries
 * remain fast on indexed columns without full-collection scans.
 *
 * reelId is a MongoDB ObjectId stored as a VARCHAR(64).
 */
@Entity
@Table(name = "reel_likes",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_reel_likes_reel_user",
               columnNames = {"reel_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReelLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reel_id", nullable = false, length = 64)
    private String reelId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
