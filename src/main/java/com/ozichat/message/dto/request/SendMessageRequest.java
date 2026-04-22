package com.ozichat.message.dto.request;

import com.ozichat.message.document.Message;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotNull(message = "conversationId is required")
    private Long conversationId;

    /** Plain text content — required for TEXT messages, optional caption for media messages. */
    private String content;

    /** Defaults to TEXT; set to AUDIO / VIDEO / IMAGE / DOCUMENT etc. for media messages. */
    private Message.MessageType type = Message.MessageType.TEXT;

    /** Client-generated ID used to deduplicate and swap the optimistic message on ACK. */
    private String tempId;

    /** ID of the message being replied to (thread / quote). */
    private String replyTo;

    /**
     * Media attachment metadata — required when type is AUDIO, VIDEO, IMAGE, or DOCUMENT.
     * Must be null for TEXT messages.
     */
    @Valid
    private MediaAttachmentRequest media;

    // ── Cross-field validation ──────────────────────────────────────────────

    /**
     * Non-TEXT messages must carry a media attachment.
     * TEXT messages must carry content.
     */
    @AssertTrue(message = "media is required for non-TEXT message types")
    private boolean isMediaPresent() {
        if (type == null || type == Message.MessageType.TEXT) return true;
        return media != null;
    }

    @AssertTrue(message = "content is required for TEXT messages")
    private boolean isContentPresent() {
        if (type != null && type != Message.MessageType.TEXT) return true;
        return content != null && !content.isBlank();
    }
}
