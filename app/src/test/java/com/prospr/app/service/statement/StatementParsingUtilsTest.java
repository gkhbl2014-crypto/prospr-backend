package com.prospr.app.service.statement;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class StatementParsingUtilsTest {

    @Test
    void parsesMultipleDateFormats() {
        assertThat(StatementParsingUtils.parseDate("01/04/2023")).isEqualTo(LocalDate.of(2023, 4, 1));
        assertThat(StatementParsingUtils.parseDate("01-04-2023")).isEqualTo(LocalDate.of(2023, 4, 1));
        assertThat(StatementParsingUtils.parseDate("2023-04-01")).isEqualTo(LocalDate.of(2023, 4, 1));
    }

    @Test
    void returnsNullForUnparseableDate() {
        assertThat(StatementParsingUtils.parseDate("not-a-date")).isNull();
        assertThat(StatementParsingUtils.parseDate(null)).isNull();
    }

    @Test
    void parsesAmountsStrippingCommasAndCurrencySymbol() {
        assertThat(StatementParsingUtils.parseAmount("1,234.50")).isEqualByComparingTo("1234.50");
        assertThat(StatementParsingUtils.parseAmount("₹500.00")).isEqualByComparingTo("500.00");
        assertThat(StatementParsingUtils.parseAmount(null)).isNull();
        assertThat(StatementParsingUtils.parseAmount("garbage")).isNull();
    }

    @Test
    void mapColumnsMatchesCaseInsensitiveHeaderNames() {
        Map<String, Integer> columns = StatementParsingUtils.mapColumns(
                new String[] {"Txn Date", "Description", "Debit", "Credit", "Balance", "Ref No"});

        assertThat(columns.get("date")).isEqualTo(0);
        assertThat(columns.get("narration")).isEqualTo(1);
        assertThat(columns.get("debit")).isEqualTo(2);
        assertThat(columns.get("credit")).isEqualTo(3);
        assertThat(columns.get("balance")).isEqualTo(4);
        assertThat(columns.get("reference")).isEqualTo(5);
    }

    @Test
    void parseRowUsesDebitColumnWhenPresent() {
        Map<String, Integer> columns = StatementParsingUtils.mapColumns(
                new String[] {"Date", "Narration", "Debit", "Credit", "Balance"});
        String[] cells = {"01/04/2023", "Zomato order", "500.00", "", "45230.00"};

        Optional<ParsedTransactionRow> row = StatementParsingUtils.parseRow(cells, columns);

        assertThat(row).isPresent();
        assertThat(row.get().type()).isEqualTo("DEBIT");
        assertThat(row.get().amount()).isEqualByComparingTo("500.00");
        assertThat(row.get().balance()).isEqualByComparingTo("45230.00");
    }

    @Test
    void parseRowIsEmptyWhenDateColumnMissingOrUnparseable() {
        Map<String, Integer> columns = StatementParsingUtils.mapColumns(new String[] {"Narration", "Amount"});
        String[] cells = {"Zomato order", "500.00"};

        assertThat(StatementParsingUtils.parseRow(cells, columns)).isEmpty();
    }

    @Test
    void parseRowIsEmptyWhenNoAmountColumnHasAValue() {
        Map<String, Integer> columns = StatementParsingUtils.mapColumns(new String[] {"Date", "Narration"});
        String[] cells = {"01/04/2023", "Opening balance note"};

        assertThat(StatementParsingUtils.parseRow(cells, columns)).isEmpty();
    }
}
