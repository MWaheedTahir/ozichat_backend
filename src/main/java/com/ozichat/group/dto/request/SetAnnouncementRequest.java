package com.ozichat.group.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetAnnouncementRequest {

    /** Set to null or empty string to clear the announcement */
    @Size(max = 1000, message = "Announcement must not exceed 1000 characters")
    private String text;
}
