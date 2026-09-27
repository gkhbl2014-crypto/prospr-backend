package com.prospr.app.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PayeePurposeResponse {
    private String counterpartyKey;
    private String displayName;
    private String purpose;
    /** The free-text note stored alongside the purpose, if any was given (e.g. "Plumber deposit"
     *  when purpose=OTHER) - null when none was provided or a previously-set one was left untouched. */
    private String label;
    /** How many of the caller's transactions resolved to this counterparty at the moment the purpose
     *  was set - not re-derived afterward, so it reflects "what you were teaching the system," not a
     *  live count. */
    private int affectedTransactionCount;
}
