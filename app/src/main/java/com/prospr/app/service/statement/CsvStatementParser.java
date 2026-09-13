package com.prospr.app.service.statement;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.prospr.app.exception.ImportValidationException;

/**
 * Expects a header row naming (in any order, any subset) date/narration or description/debit/
 * credit/amount/balance/reference or "ref no" - matched case-insensitively as a substring, per
 * {@link StatementParsingUtils#mapColumns}.
 */
@Component
public class CsvStatementParser implements StatementParser {

    @Override
    public boolean supports(String filename, String contentType) {
        return (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".csv"))
                || "text/csv".equalsIgnoreCase(contentType);
    }

    @Override
    public List<ParsedTransactionRow> parse(byte[] fileBytes) {
        List<ParsedTransactionRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(fileBytes), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return rows;
            }
            Map<String, Integer> columns = StatementParsingUtils.mapColumns(splitLine(headerLine));

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                StatementParsingUtils.parseRow(splitLine(line), columns).ifPresent(rows::add);
            }
        } catch (IOException ex) {
            throw new ImportValidationException("Unable to read this CSV file", ex);
        }
        return rows;
    }

    /** Comma-split with a quoted-field guard - enough for a fixed, self-documented column format. */
    private String[] splitLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
