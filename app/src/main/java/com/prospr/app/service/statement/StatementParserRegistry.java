package com.prospr.app.service.statement;

import java.util.List;

import org.springframework.stereotype.Component;

import com.prospr.app.exception.ImportValidationException;

/** Picks the right {@link StatementParser} by filename/content-type, mirroring the account-type
 *  dispatch already used for Setu XML in SetuSessionService. */
@Component
public class StatementParserRegistry {

    private final List<StatementParser> parsers;

    public StatementParserRegistry(List<StatementParser> parsers) {
        this.parsers = parsers;
    }

    public List<ParsedTransactionRow> parse(String filename, String contentType, byte[] fileBytes) {
        StatementParser parser = parsers.stream()
                .filter(p -> p.supports(filename, contentType))
                .findFirst()
                .orElseThrow(() -> new ImportValidationException(
                        "Unsupported file type - only PDF, CSV, and XLSX bank statements are supported"));
        return parser.parse(fileBytes);
    }
}
