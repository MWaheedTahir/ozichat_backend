package com.ozichat.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterDeviceTokenRequest {

    @NotBlank(message = "FCM token is required")
    private String token;

    /** ANDROID | IOS | WEB */
    private String platform = "ANDROID";

    /** Optional: device model name, e.g. "Samsung Galaxy S24" */
    private String deviceName;
}
