package com.prospr.app.service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.prospr.app.entity.Insurance;

/** Shared "is this policy currently active" rule, used by both Health and Life coverage services. */
@Component
public class InsuranceActivityEvaluator {

    private static final Set<String> INACTIVE_STATUSES = Set.of("EXPIRED", "LAPSED", "CANCELLED", "CLOSED");

    public boolean isActive(Insurance policy) {
        String status = policy.getPolicyStatus();
        if (status != null && !status.isBlank()) {
            String upper = status.toUpperCase(Locale.ROOT);
            if (INACTIVE_STATUSES.contains(upper)) {
                return false;
            }
            if ("ACTIVE".equals(upper)) {
                return true;
            }
        }
        LocalDate endDate = policy.getPolicyEndDate();
        return endDate == null || !endDate.isBefore(LocalDate.now());
    }
}
