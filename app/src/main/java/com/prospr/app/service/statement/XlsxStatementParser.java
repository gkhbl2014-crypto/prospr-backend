package com.prospr.app.service.statement;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.prospr.app.exception.ImportValidationException;

/** Reads the first sheet only; same header-mapping convention as {@link CsvStatementParser}. */
@Component
public class XlsxStatementParser implements StatementParser {

    private static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Override
    public boolean supports(String filename, String contentType) {
        return (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".xlsx"))
                || CONTENT_TYPE.equalsIgnoreCase(contentType);
    }

    @Override
    public List<ParsedTransactionRow> parse(byte[] fileBytes) {
        List<ParsedTransactionRow> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Iterator<Row> rowIterator = sheet.rowIterator();
            if (!rowIterator.hasNext()) {
                return rows;
            }

            Map<String, Integer> columns = StatementParsingUtils.mapColumns(toCells(rowIterator.next(), formatter));
            while (rowIterator.hasNext()) {
                StatementParsingUtils.parseRow(toCells(rowIterator.next(), formatter), columns).ifPresent(rows::add);
            }
        } catch (IOException ex) {
            throw new ImportValidationException("Unable to read this XLSX file", ex);
        }
        return rows;
    }

    /** Normalizes every cell to text regardless of Excel's underlying numeric/date formatting. */
    private String[] toCells(Row row, DataFormatter formatter) {
        int lastColumn = row.getLastCellNum();
        if (lastColumn < 0) {
            return new String[0];
        }
        String[] cells = new String[lastColumn];
        for (int i = 0; i < lastColumn; i++) {
            Cell cell = row.getCell(i);
            cells[i] = cell == null ? "" : formatter.formatCellValue(cell);
        }
        return cells;
    }
}
