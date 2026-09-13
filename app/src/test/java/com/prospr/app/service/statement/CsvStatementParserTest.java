package com.prospr.app.service.statement;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class CsvStatementParserTest {

    private final CsvStatementParser parser = new CsvStatementParser();

    @Test
    void supportsCsvByExtensionOrContentType() {
        assertThat(parser.supports("statement.csv", null)).isTrue();
        assertThat(parser.supports("statement.PDF", "text/csv")).isTrue();
        assertThat(parser.supports("statement.pdf", "application/pdf")).isFalse();
    }

    @Test
    void parsesHeaderMappedColumnsRegardlessOfCase() {
        String csv = "Date,Narration,Debit,Credit,Balance,Reference\n"
                + "01/04/2023,Zomato order,500.00,,45230.00,REF1\n"
                + "02/04/2023,Salary credit,,50000.00,95230.00,REF2\n";

        List<ParsedTransactionRow> rows = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).type()).isEqualTo("DEBIT");
        assertThat(rows.get(0).amount()).isEqualByComparingTo("500.00");
        assertThat(rows.get(0).reference()).isEqualTo("REF1");
        assertThat(rows.get(1).type()).isEqualTo("CREDIT");
        assertThat(rows.get(1).amount()).isEqualByComparingTo("50000.00");
    }

    @Test
    void handlesMissingOptionalColumns() {
        String csv = "Date,Amount\n01/04/2023,-500.00\n";

        List<ParsedTransactionRow> rows = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).type()).isEqualTo("DEBIT");
        assertThat(rows.get(0).amount()).isEqualByComparingTo("500.00");
        assertThat(rows.get(0).narration()).isNull();
    }

    @Test
    void skipsBlankRowsAndRowsWithNoUsableData() {
        String csv = "Date,Narration,Amount\n"
                + "\n"
                + "01/04/2023,No amount at all,\n"
                + "02/04/2023,Has amount,250.00\n";

        List<ParsedTransactionRow> rows = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).narration()).isEqualTo("Has amount");
    }

    @Test
    void returnsEmptyListForEmptyFile() {
        assertThat(parser.parse(new byte[0])).isEmpty();
    }
}
