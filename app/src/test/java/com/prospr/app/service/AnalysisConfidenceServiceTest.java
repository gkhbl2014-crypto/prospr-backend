package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class AnalysisConfidenceServiceTest {

    private static AnalysisConfidenceService.MonthCoverage month(String spend, String unexplained) {
        return new AnalysisConfidenceService.MonthCoverage(new BigDecimal(spend), new BigDecimal(unexplained));
    }

    private static List<AnalysisConfidenceService.MonthCoverage> months(int count, String spend, String unexplained) {
        return Collections.nCopies(count, month(spend, unexplained));
    }

    @Test
    void coverageCountsOtherDebitAndUnknownAsUnexplainedAndAddsUnknownToSpend() {
        // totalCashOutflow already contains OTHER_DEBIT (2000); UNKNOWN (500) sits outside it.
        AnalysisConfidenceService.MonthCoverage coverage = AnalysisConfidenceService.coverageOf(
                new BigDecimal("10000"), new BigDecimal("2000"), new BigDecimal("500"));

        assertThat(coverage.spend()).isEqualByComparingTo("10500");
        assertThat(coverage.unexplained()).isEqualByComparingTo("2500");
        assertThat(AnalysisConfidenceService.explainedPercent(coverage)).isEqualTo(76);
    }

    @Test
    void explainedPercentIsNullWhenThereIsNothingSpent() {
        assertThat(AnalysisConfidenceService.explainedPercent(month("0", "0"))).isNull();
        assertThat(AnalysisConfidenceService.coverageOf(null, null, null).spend()).isEqualByComparingTo("0");
    }

    @Test
    void wellCategorizedSpendingWithFullHistoryIsHighConfidence() {
        AnalysisConfidenceService.Confidence confidence = AnalysisConfidenceService.evaluate(
                month("20000", "1000"), months(6, "20000", "1000"), 6);

        assertThat(confidence.level()).isEqualTo(AnalysisConfidenceService.HIGH);
        assertThat(confidence.explainedPercent()).isEqualTo(95);
    }

    @Test
    void mostlyUncategorizedSpendingIsLowConfidenceAndSaysHowMuch() {
        AnalysisConfidenceService.Confidence confidence = AnalysisConfidenceService.evaluate(
                month("20000", "12000"), months(6, "20000", "12000"), 6);

        assertThat(confidence.level()).isEqualTo(AnalysisConfidenceService.LOW);
        assertThat(confidence.reason()).contains("60%").contains("12,000");
        assertThat(confidence.unexplainedAmount()).isEqualByComparingTo("12000");
    }

    @Test
    void aModerateUncategorizedShareIsMediumConfidence() {
        AnalysisConfidenceService.Confidence confidence = AnalysisConfidenceService.evaluate(
                month("20000", "6000"), months(6, "20000", "6000"), 6);

        assertThat(confidence.level()).isEqualTo(AnalysisConfidenceService.MEDIUM);
    }

    @Test
    void tooLittleHistoryIsLowConfidenceEvenWhenEverythingIsCategorized() {
        AnalysisConfidenceService.Confidence confidence = AnalysisConfidenceService.evaluate(
                month("20000", "0"), months(2, "20000", "0"), 6);

        assertThat(confidence.level()).isEqualTo(AnalysisConfidenceService.LOW);
        assertThat(confidence.reason()).contains("2 months");
    }

    @Test
    void someButNotFullHistoryCapsConfidenceAtMedium() {
        AnalysisConfidenceService.Confidence confidence = AnalysisConfidenceService.evaluate(
                month("20000", "0"), months(4, "20000", "0"), 6);

        assertThat(confidence.level()).isEqualTo(AnalysisConfidenceService.MEDIUM);
        assertThat(confidence.reason()).contains("4 months");
    }

    @Test
    void aSuddenCoverageImprovementIsFlaggedBecauseItCanLookLikeSpendingGrowth() {
        // Baseline months had 18% unexplained, this month only 0% - e.g. the user just answered the
        // review queue. Category totals can jump from re-categorization alone.
        AnalysisConfidenceService.Confidence confidence = AnalysisConfidenceService.evaluate(
                month("20000", "0"), months(6, "20000", "3600"), 6);

        assertThat(confidence.level()).isEqualTo(AnalysisConfidenceService.MEDIUM);
        assertThat(confidence.reason()).contains("re-categorization");
    }
}
