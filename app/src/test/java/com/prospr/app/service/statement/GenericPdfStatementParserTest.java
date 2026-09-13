package com.prospr.app.service.statement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class GenericPdfStatementParserTest {

    private final GenericPdfStatementParser parser = new GenericPdfStatementParser();

    @Test
    void parsesStandardDateNarrationDebitCreditBalanceLine() throws IOException {
        byte[] pdf = buildPdf(List.of("01/04/2023 UPI Zomato Order 500.00 0.00 45230.00"));

        List<ParsedTransactionRow> rows = parser.parse(pdf);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).type()).isEqualTo("DEBIT");
        assertThat(rows.get(0).amount()).isEqualByComparingTo("500.00");
        assertThat(rows.get(0).balance()).isEqualByComparingTo("45230.00");
    }

    @Test
    void handlesCreditMarkerWhenOnlyOneAmountIsPresent() throws IOException {
        byte[] pdf = buildPdf(List.of("02/04/2023 Salary Credit 50000.00 CR"));

        List<ParsedTransactionRow> rows = parser.parse(pdf);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).type()).isEqualTo("CREDIT");
        assertThat(rows.get(0).amount()).isEqualByComparingTo("50000.00");
    }

    @Test
    void handlesDDMMYYYYAndYYYYMMDDDateFormats() throws IOException {
        byte[] pdf = buildPdf(List.of(
                "01-04-2023 Line one 100.00 900.00",
                "2023-04-02 Line two 200.00 700.00"));

        List<ParsedTransactionRow> rows = parser.parse(pdf);

        assertThat(rows).hasSize(2);
    }

    @Test
    void parsesLeadingSerialNumberTextualMonthDateAndSingleDecimalBalance() throws IOException {
        byte[] pdf = buildPdf(List.of(
                "2 01 Mar 2026 UPI/airtel/634031990331/AirtelBroadband UPI-606074019651 1,060.82 1,46,115.6"));

        List<ParsedTransactionRow> rows = parser.parse(pdf);

        assertThat(rows).hasSize(1);
        ParsedTransactionRow row = rows.get(0);
        assertThat(row.valueDate()).isEqualTo(java.time.LocalDate.of(2026, 3, 1));
        assertThat(row.narration()).isEqualTo("UPI/airtel/634031990331/AirtelBroadband");
        assertThat(row.reference()).isEqualTo("UPI-606074019651");
        assertThat(row.amount()).isEqualByComparingTo("1060.82");
        assertThat(row.balance()).isEqualByComparingTo("146115.6");
        assertThat(row.type()).isEqualTo("DEBIT");
    }

    @Test
    void reassemblesNarrationThatWrapsAcrossMultiplePdfTextLines() throws IOException {
        byte[] pdf = buildPdf(List.of(
                "21 07 Mar 2026 UPI/Spotify India",
                "L/109330198798/MandateRequest",
                "UPI-606680056698 119.00 1,27,209.33",
                "22 07 Mar 2026 UPI/Zepto/539411833928/Payment from Ph UPI-606632374024 114.00 1,27,095.3"));

        List<ParsedTransactionRow> rows = parser.parse(pdf);

        assertThat(rows).hasSize(2);
        ParsedTransactionRow wrapped = rows.get(0);
        assertThat(wrapped.valueDate()).isEqualTo(java.time.LocalDate.of(2026, 3, 7));
        assertThat(wrapped.narration()).isEqualTo("UPI/Spotify India L/109330198798/MandateRequest");
        assertThat(wrapped.reference()).isEqualTo("UPI-606680056698");
        assertThat(wrapped.amount()).isEqualByComparingTo("119.00");
        assertThat(wrapped.balance()).isEqualByComparingTo("127209.33");

        ParsedTransactionRow single = rows.get(1);
        assertThat(single.narration()).isEqualTo("UPI/Zepto/539411833928/Payment from Ph");
        assertThat(single.reference()).isEqualTo("UPI-606632374024");
        assertThat(single.amount()).isEqualByComparingTo("114.00");
        assertThat(single.balance()).isEqualByComparingTo("127095.3");
    }

    @Test
    void skipsHeaderAndFooterNoiseLines() throws IOException {
        byte[] pdf = buildPdf(List.of("Account Statement", "Page 1 of 3", "IFSC HDFC0001234"));

        assertThat(parser.parse(pdf)).isEmpty();
    }

    @Test
    void supportsPdfByExtensionOrContentType() {
        assertThat(parser.supports("statement.pdf", null)).isTrue();
        assertThat(parser.supports("statement.PDF", "application/pdf")).isTrue();
        assertThat(parser.supports("statement.csv", "text/csv")).isFalse();
    }

    private byte[] buildPdf(List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                stream.newLineAtOffset(50, 700);
                for (String line : lines) {
                    stream.showText(line);
                    stream.newLineAtOffset(0, -15);
                }
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
