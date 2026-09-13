package com.prospr.app.service.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Date/decimal parsing and header-column mapping shared by every {@link StatementParser}. */
public final class StatementParsingUtils {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            // Textual-month formats - e.g. "01 Mar 2026", as many Indian bank statement PDFs print.
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH));

    private StatementParsingUtils() {
    }

    public static LocalDate parseDate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String trimmed = token.trim();
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return null;
    }

    public static BigDecimal parseAmount(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(token.replace(",", "").replace("₹", "").trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Case-insensitive substring match against a small fixed set of expected column names. */
    public static Map<String, Integer> mapColumns(String[] headerCells) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < headerCells.length; i++) {
            String header = headerCells[i] == null ? "" : headerCells[i].trim().toLowerCase(Locale.ROOT);
            if (header.contains("date")) {
                columns.putIfAbsent("date", i);
            } else if (header.contains("narration") || header.contains("description")) {
                columns.putIfAbsent("narration", i);
            } else if (header.contains("debit")) {
                columns.putIfAbsent("debit", i);
            } else if (header.contains("credit")) {
                columns.putIfAbsent("credit", i);
            } else if (header.equals("amount")) {
                columns.putIfAbsent("amount", i);
            } else if (header.contains("balance")) {
                columns.putIfAbsent("balance", i);
            } else if (header.contains("reference") || header.contains("ref no") || header.equals("ref")) {
                columns.putIfAbsent("reference", i);
            }
        }
        return columns;
    }

    /** Builds one row from already-stringified cells (CSV cells, or XLSX cells normalized to text). */
    public static Optional<ParsedTransactionRow> parseRow(String[] cells, Map<String, Integer> columns) {
        Integer dateIndex = columns.get("date");
        if (dateIndex == null || dateIndex >= cells.length) {
            return Optional.empty();
        }
        LocalDate date = parseDate(cells[dateIndex]);
        if (date == null) {
            return Optional.empty();
        }

        String narration = cellOrNull(cells, columns.get("narration"));
        String reference = cellOrNull(cells, columns.get("reference"));
        BigDecimal debit = decimalCellOrNull(cells, columns.get("debit"));
        BigDecimal credit = decimalCellOrNull(cells, columns.get("credit"));
        BigDecimal balance = decimalCellOrNull(cells, columns.get("balance"));
        BigDecimal rawAmount = decimalCellOrNull(cells, columns.get("amount"));

        BigDecimal amount;
        String type;
        if (debit != null && debit.compareTo(BigDecimal.ZERO) != 0) {
            amount = debit;
            type = "DEBIT";
        } else if (credit != null && credit.compareTo(BigDecimal.ZERO) != 0) {
            amount = credit;
            type = "CREDIT";
        } else if (rawAmount != null) {
            amount = rawAmount.abs();
            type = rawAmount.compareTo(BigDecimal.ZERO) < 0 ? "DEBIT" : "CREDIT";
        } else {
            return Optional.empty();
        }

        return Optional.of(new ParsedTransactionRow(date, narration, debit, credit, amount, balance, type, reference));
    }

    private static String cellOrNull(String[] cells, Integer index) {
        if (index == null || index >= cells.length) {
            return null;
        }
        String value = cells[index] == null ? null : cells[index].trim();
        return (value == null || value.isEmpty()) ? null : value;
    }

    private static BigDecimal decimalCellOrNull(String[] cells, Integer index) {
        return parseAmount(cellOrNull(cells, index));
    }
}
