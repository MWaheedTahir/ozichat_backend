package com.ozichat.message.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "messages")
@CompoundIndexes({
    @CompoundIndex(name = "idx_conv_created", def = "{'conversationId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "idx_conv_status", def = "{'conversationId': 1, 'status': 1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    private String id;

    @Field("conversationId")
    private Long conversationId;

    @Field("senderId")
    private Long senderId;

    @Field("content")
    private String content;

    @Field("type")
    @Builder.Default
    private MessageType type = MessageType.TEXT;

    @Field("status")
    @Builder.Default
    private MessageStatus status = MessageStatus.SENT;

    @Field("replyTo")
    private String replyTo;

    @Field("forwardedFrom")
    private String forwardedFrom;

    @Field("readAt")
    private Instant readAt;

    @Field("media")
    private MediaAttachment media;

    @Field("isEdited")
    @Builder.Default
    private Boolean isEdited = false;

    @Field("editedAt")
    private Instant editedAt;

    @Field("isDeletedForEveryone")
    @Builder.Default
    private Boolean isDeletedForEveryone = false;

    @Indexed(sparse = true)
    @Field("disappearsAt")
    private Instant disappearsAt;

    @Field("tempId")
    private String tempId;

    @Field("createdAt")
    private Instant createdAt;

    @Field("updatedAt")
    private Instant updatedAt;

    public enum MessageType {
        TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT, LOCATION, CONTACT
    }

    public enum MessageStatus {
        SENT, DELIVERED, READ
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MediaAttachment {
        /** S3 object key — kept so we can delete the file when a message is deleted for everyone. */
        private String s3Key;
        private String url;
        private String thumbnailUrl;
        private String mimeType;
        private Long fileSize;
        private String fileName;
        /** Duration in seconds — populated for AUDIO and VIDEO messages. */
        private Integer duration;
        private Integer width;
        private Integer height;
    }
}
