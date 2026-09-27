package com.prospr.app.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PayeePurposeResponse {
    private String counterpartyKey;
    private String displayName;
    private String purpose;
    /** How many of the caller's transactions resolved to this counterparty at the moment the purpose
     *  was set - not re-derived afterward, so it reflects "what you were teaching the system," not a
     *  live count. */
    private int affectedTransactionCount;
}
