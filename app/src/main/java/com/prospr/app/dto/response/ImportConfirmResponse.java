package com.prospr.app.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImportConfirmResponse {

    private int imported;
    private List<TransactionResponse> transactions;
}
