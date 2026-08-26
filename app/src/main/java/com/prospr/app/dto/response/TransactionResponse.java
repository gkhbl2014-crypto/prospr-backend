package com.prospr.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TransactionResponse {

    private UUID id;
    private UUID memberId;
    private String maskedAccountNumber;
    private String txnId;
    private String mode;
    private String type;
    private BigDecimal amount;
    private BigDecimal transactionalBalance;
    private String narration;
    private String reference;
    private LocalDate valueDate;
    private OffsetDateTime transactionTimestamp;
}
