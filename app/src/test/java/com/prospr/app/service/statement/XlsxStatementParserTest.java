package com.prospr.app.service.statement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class XlsxStatementParserTest {

    private final XlsxStatementParser parser = new XlsxStatementParser();

    @Test
    void supportsXlsxByExtensionOrContentType() {
        assertThat(parser.supports("statement.xlsx", null)).isTrue();
        assertThat(parser.supports("statement.csv", "text/csv")).isFalse();
    }

    @Test
    void parsesHeaderRowAndDataRows() throws IOException {
        byte[] xlsx = buildWorkbook(
                new String[] {"Date", "Narration", "Debit", "Credit", "Balance"},
                new String[][] {
                        {"01/04/2023", "Zomato order", "500", "", "45230"},
                        {"02/04/2023", "Salary credit", "", "50000", "95230"},
                });

        List<ParsedTransactionRow> rows = parser.parse(xlsx);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).type()).isEqualTo("DEBIT");
        assertThat(rows.get(1).type()).isEqualTo("CREDIT");
    }

    @Test
    void normalizesNumericCellsRegardlessOfExcelFormatting() throws IOException {
        byte[] xlsx = buildWorkbook(
                new String[] {"Date", "Narration", "Amount"},
                new String[][] {{"01/04/2023", "Some purchase", "1234.50"}});

        List<ParsedTransactionRow> rows = parser.parse(xlsx);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).amount()).isEqualByComparingTo("1234.50");
    }

    @Test
    void returnsEmptyListWhenSheetHasOnlyAHeaderRow() throws IOException {
        byte[] xlsx = buildWorkbook(new String[] {"Date", "Narration", "Amount"}, new String[][] {});

        assertThat(parser.parse(xlsx)).isEmpty();
    }

    private byte[] buildWorkbook(String[] header, String[][] dataRows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Statement");
            writeRow(sheet, 0, header);
            for (int i = 0; i < dataRows.length; i++) {
                writeRow(sheet, i + 1, dataRows[i]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void writeRow(Sheet sheet, int rowIndex, String[] values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }
}
