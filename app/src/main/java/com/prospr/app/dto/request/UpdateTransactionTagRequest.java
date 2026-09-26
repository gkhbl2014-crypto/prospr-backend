package com.prospr.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** {@code tag} is one of INVESTMENT | ESSENTIAL | LIFESTYLE_CREEP | AUTO (AUTO clears the override,
 *  reverting to whatever the system would derive on its own) - validated in the controller against
 *  that exact set rather than trusting arbitrary client input. */
@Getter
@Setter
public class UpdateTransactionTagRequest {

    @NotBlank
    private String tag;
}
