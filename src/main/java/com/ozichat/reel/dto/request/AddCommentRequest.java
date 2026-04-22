package com.ozichat.reel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddCommentRequest {

    @NotBlank(message = "Comment content is required")
    @Size(min = 1, max = 1000, message = "Comment must be between 1 and 1000 characters")
    private String content;
}
