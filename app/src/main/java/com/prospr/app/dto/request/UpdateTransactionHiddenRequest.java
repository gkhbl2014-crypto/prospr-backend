package com.prospr.app.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTransactionHiddenRequest {

    @NotNull
    private Boolean hidden;
}
