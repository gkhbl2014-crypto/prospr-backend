package com.prospr.app.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** No server-side staging exists between preview and confirm - the frontend echoes back exactly
 *  the rows the user kept checked, after any inline edits, as plain values (not row IDs). */
@Getter
@Setter
public class ImportConfirmRow {

    @NotNull
    private LocalDate valueDate;

    private String narration;

    @NotNull
    private String type;

    @NotNull
    private BigDecimal amount;

    private BigDecimal balance;
    private String reference;
}
