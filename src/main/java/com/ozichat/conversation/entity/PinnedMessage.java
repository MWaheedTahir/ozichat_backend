package com.ozichat.conversation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Tracks which messages are pinned in a conversation.
 * messageId is a MongoDB ObjectId (String).
 * A conversation can have up to ~5 pinned messages (enforced at service layer).
 */
@Entity
@Table(name = "pinned_messages",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_pinned_conv_msg",
               columnNames = {"conversation_id", "message_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PinnedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** MongoDB ObjectId of the pinned message */
    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    @Column(name = "pinned_by", nullable = false)
    private Long pinnedBy;

    @CreationTimestamp
    @Column(name = "pinned_at", updatable = false)
    private Instant pinnedAt;
}
