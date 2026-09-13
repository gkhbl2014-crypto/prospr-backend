package com.prospr.app.service.statement;

import java.util.List;

/**
 * One format-specific bank statement parser. New bank/format-specific implementations can be added
 * without touching existing ones or the registry - just add another {@code @Component}.
 */
public interface StatementParser {

    boolean supports(String filename, String contentType);

    List<ParsedTransactionRow> parse(byte[] fileBytes);
}
