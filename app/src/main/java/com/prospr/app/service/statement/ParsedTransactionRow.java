package com.prospr.app.service.statement;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One transaction line extracted from an uploaded statement, before the user has confirmed it. */
public record ParsedTransactionRow(LocalDate valueDate, String narration, BigDecimal debit, BigDecimal credit,
                                    BigDecimal amount, BigDecimal balance, String type, String reference) {
}
