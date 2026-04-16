package com.ozichat.group.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateGroupRequest {

    @NotBlank(message = "Group name is required")
    @Size(min = 2, max = 100, message = "Group name must be between 2 and 100 characters")
    private String groupName;

    @Size(max = 500)
    private String groupDescription;

    private String groupAvatarUrl;

    @NotEmpty(message = "At least one member is required")
    private List<Long> memberIds;
}
