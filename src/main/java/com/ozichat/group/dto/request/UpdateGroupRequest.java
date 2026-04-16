package com.ozichat.group.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateGroupRequest {

    @Size(min = 2, max = 100)
    private String groupName;

    @Size(max = 500)
    private String groupDescription;

    private String groupAvatarUrl;
    private Boolean onlyAdminsCanSend;
    private Boolean onlyAdminsCanEditInfo;
}
