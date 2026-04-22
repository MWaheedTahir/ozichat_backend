package com.ozichat.reel.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "reel_comments")
@CompoundIndexes({
    @CompoundIndex(name = "idx_reel_comments_reel_created",
                   def = "{'reelId': 1, 'isDeleted': 1, 'createdAt': -1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReelComment {

    @Id
    private String id;

    @Field("reelId")
    private String reelId;

    @Field("userId")
    private Long userId;

    @Field("content")
    private String content;

    @Field("isDeleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Field("deletedAt")
    private Instant deletedAt;

    @Field("createdAt")
    private Instant createdAt;

    @Field("updatedAt")
    private Instant updatedAt;
}
