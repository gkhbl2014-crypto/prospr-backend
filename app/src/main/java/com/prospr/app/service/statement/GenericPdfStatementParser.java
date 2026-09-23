package com.prospr.app.service.statement;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.prospr.app.exception.ImportValidationException;

/**
 * Best-effort, bank-format-agnostic PDF statement parser. Extracts raw text via PDFBox and applies
 * one generic line pattern: a leading date, narration, one-to-three trailing decimal amounts, and
 * an optional trailing Cr/Dr marker. Real per-bank layouts (HDFC/ICICI/SBI, etc.) vary enough that
 * accurate bank-specific parsing is future work once real sample statements are available to build
 * against - this parser is a v1 baseline, not a claim of broad accuracy. Lines that don't match
 * (headers, footers, page numbers) are silently skipped, not treated as errors.
 *
 * <p>A single transaction's narration is often too long for one PDF text line and wraps onto the
 * next (and sometimes a third) line before the trailing amount columns appear. Lines are therefore
 * first reassembled into logical records - a line starting with a (serial number +) date opens a
 * new record, and any following line that doesn't itself start a new record is folded into it -
 * before the line pattern above is applied to each reassembled record.
 */
@Component
public class GenericPdfStatementParser implements StatementParser {

    private static final String DATE_ALTERNATION = "\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4}|\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}";
    private static final Pattern RECORD_START_PATTERN = Pattern.compile(
            "^\\s*(?:\\d+\\s+)?(?:" + DATE_ALTERNATION + ")\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^\\s*(?:\\d+\\s+)?"
                    + "(" + DATE_ALTERNATION + ")\\s+"
                    + "(.+?)\\s+"
                    + "((?:[\\d,]+\\.\\d{1,2}\\s*){1,3})\\s*(CR|DR)?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[\\d,]+\\.\\d{1,2}");
    private static final Pattern REFERENCE_TOKEN_PATTERN = Pattern.compile("^[A-Za-z]*-?\\d{6,}$");

    @Override
    public boolean supports(String filename, String contentType) {
        return (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf"))
                || "application/pdf".equalsIgnoreCase(contentType);
    }

    @Override
    public List<ParsedTransactionRow> parse(byte[] fileBytes) {
        List<ParsedTransactionRow> rows = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            String text = new PDFTextStripper().getText(document);
            BigDecimal previousBalance = null;
            for (String record : reassembleRecords(text)) {
                Optional<ParsedTransactionRow> row = parseLine(record, previousBalance);
                if (row.isPresent()) {
                    rows.add(row.get());
                    if (row.get().balance() != null) {
                        previousBalance = row.get().balance();
                    }
                }
            }
        } catch (IOException ex) {
            throw new ImportValidationException("Unable to read this PDF file", ex);
        }
        return rows;
    }

    /**
     * Folds narration-wrapped continuation lines back into the record they belong to. A line
     * starting with a (serial number +) date opens a new record; any line before the first such
     * date-starting line is discarded as noise (headers/footers), never a "record" itself.
     */
    private List<String> reassembleRecords(String text) {
        List<String> records = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        boolean recording = false;
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (RECORD_START_PATTERN.matcher(line).find()) {
                if (recording) {
                    records.add(buffer.toString());
                }
                buffer = new StringBuilder(line);
                recording = true;
            } else if (recording) {
                buffer.append(' ').append(line);
            }
        }
        if (recording) {
            records.add(buffer.toString());
        }
        return records;
    }

    private Optional<ParsedTransactionRow> parseLine(String line, BigDecimal previousBalance) {
        Matcher matcher = LINE_PATTERN.matcher(line.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        LocalDate date = StatementParsingUtils.parseDate(matcher.group(1));
        if (date == null) {
            return Optional.empty();
        }
        NarrationAndReference narrationAndReference = splitTrailingReference(matcher.group(2).trim());
        List<BigDecimal> numbers = extractNumbers(matcher.group(3));
        if (numbers.isEmpty()) {
            return Optional.empty();
        }
        String marker = matcher.group(4);

        return Optional.of(buildRow(date, narrationAndReference, numbers, marker, previousBalance));
    }

    /**
     * Many statement lines end narration with a trailing reference/UTR token (e.g. "UPI-606074019651")
     * that is not itself part of the human-readable description. Split it out into its own field when
     * it looks like a reference (mostly digits, 6+ long), otherwise leave the narration untouched.
     */
    private NarrationAndReference splitTrailingReference(String narration) {
        int lastSpace = narration.lastIndexOf(' ');
        if (lastSpace < 0) {
            return new NarrationAndReference(narration, null);
        }
        String lastToken = narration.substring(lastSpace + 1);
        if (REFERENCE_TOKEN_PATTERN.matcher(lastToken).matches()) {
            return new NarrationAndReference(narration.substring(0, lastSpace).trim(), lastToken);
        }
        return new NarrationAndReference(narration, null);
    }

    private record NarrationAndReference(String narration, String reference) {
    }

    private List<BigDecimal> extractNumbers(String group) {
        List<BigDecimal> numbers = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(group);
        while (matcher.find()) {
            BigDecimal amount = StatementParsingUtils.parseAmount(matcher.group());
            if (amount != null) {
                numbers.add(amount);
            }
        }
        return numbers;
    }

    /**
     * 3 trailing numbers -> treated as a (debit, credit, balance) columnar layout, matching how
     * many Indian bank statements print separate Debit/Credit/Balance columns (one of the first two
     * is 0.00 for any given line) - direction is read directly from which column is non-zero, no
     * inference needed. 2 numbers -> (amount, balance): an explicit Cr/Dr marker wins when present,
     * otherwise direction is inferred from whether this line's balance increased or decreased versus
     * the previous line's balance (a real, reliable signal for this statement shape - a running
     * balance is not optional information, unlike a marker some banks simply never print). Only the
     * very first record in a file (no prior balance to compare against) falls back to
     * marker-or-default-DEBIT, an honestly-documented residual gap affecting at most one row per
     * import. 1 number only (no balance at all) has no signal beyond the marker either way.
     */
    private ParsedTransactionRow buildRow(LocalDate date, NarrationAndReference narrationAndReference,
                                           List<BigDecimal> numbers, String marker, BigDecimal previousBalance) {
        String narration = narrationAndReference.narration();
        BigDecimal debit = null;
        BigDecimal credit = null;
        BigDecimal balance = null;
        BigDecimal amount;
        String type;

        if (numbers.size() >= 3) {
            debit = nonZeroOrNull(numbers.get(0));
            credit = nonZeroOrNull(numbers.get(1));
            balance = numbers.get(2);
            amount = debit != null ? debit : (credit != null ? credit : numbers.get(0));
            type = debit != null ? "DEBIT" : "CREDIT";
        } else if (numbers.size() == 2) {
            amount = numbers.get(0);
            balance = numbers.get(1);
            type = inferType(marker, balance, previousBalance);
        } else {
            amount = numbers.get(0);
            type = inferType(marker, null, previousBalance);
        }

        return new ParsedTransactionRow(
                date, narration, debit, credit, amount, balance, type, narrationAndReference.reference());
    }

    private BigDecimal nonZeroOrNull(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) == 0 ? null : value;
    }

    private String inferType(String marker, BigDecimal balance, BigDecimal previousBalance) {
        if ("CR".equalsIgnoreCase(marker)) {
            return "CREDIT";
        }
        if ("DR".equalsIgnoreCase(marker)) {
            return "DEBIT";
        }
        if (balance != null && previousBalance != null) {
            return balance.compareTo(previousBalance) > 0 ? "CREDIT" : "DEBIT";
        }
        return "DEBIT";
    }
}
