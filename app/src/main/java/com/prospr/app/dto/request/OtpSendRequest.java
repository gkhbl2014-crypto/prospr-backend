package com.prospr.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OtpSendRequest {

    @NotBlank(message = "Mobile number cannot be blank")
    private String phone;
}
