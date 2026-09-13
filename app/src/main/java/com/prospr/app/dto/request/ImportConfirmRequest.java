package com.prospr.app.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImportConfirmRequest {

    @NotEmpty
    @Valid
    private List<ImportConfirmRow> rows;
}
