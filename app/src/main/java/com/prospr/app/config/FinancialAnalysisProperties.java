package com.prospr.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/** Tunables for the financial analysis engine that don't belong to a more specific properties class. */
@Configuration
@ConfigurationProperties(prefix = "financial-analysis")
@Getter
@Setter
public class FinancialAnalysisProperties {

    /** How many trailing calendar months MonthlySnapshotService recomputes on every run. */
    private int snapshotHistoryMonths = 12;
}
