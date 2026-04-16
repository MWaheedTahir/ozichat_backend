package com.ozichat.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendOtpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    private String email;

    /** PURPOSE: EMAIL_VERIFICATION | PASSWORD_RESET */
    @NotBlank(message = "Purpose is required")
    private String purpose;
}
