package com.prospr.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateFamilyNameRequest {

    @NotBlank
    private String familyName;
}
