package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.prospr.app.entity.EmergencyFundAllocation;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.SafetyNetSettings;
import com.prospr.app.repository.EmergencyFundAllocationRepository;

@Service
public class EmergencyFundService {

    private final EmergencyFundAllocationRepository allocationRepository;

    public EmergencyFundService(EmergencyFundAllocationRepository allocationRepository) {
        this.allocationRepository = allocationRepository;
    }

    /**
     * @param coverageMonths null when it's undefined - either no essential-expense data exists, or
     *                       essential expense is 0 (making "months of coverage" meaningless/infinite)
     * @param targetAmount   null only when averageMonthlyEssentialExpense itself is null (UNKNOWN)
     */
    public record Result(BigDecimal currentAmount, BigDecimal averageMonthlyEssentialExpense,
                          BigDecimal coverageMonths, int targetMonths, BigDecimal targetAmount,
                          BigDecimal gap, String status) {
    }

    public Result compute(Member member, SafetyNetSettings settings, EssentialExpenseService.Result essential) {
        BigDecimal currentAmount = allocationRepository
                .findByMemberIdAndIsActiveTrueOrderByCreatedAtDesc(member.getId())
                .stream()
                .map(EmergencyFundAllocation::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int targetMonths = settings.getEmergencyFundTargetMonths();
        BigDecimal avgExpense = essential.averageMonthlyEssentialExpense();

        if (avgExpense == null) {
            return new Result(currentAmount, null, null, targetMonths, null, null, EmergencyFundStatus.UNKNOWN);
        }

        if (currentAmount.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal targetAmount = avgExpense.multiply(BigDecimal.valueOf(targetMonths));
            return new Result(currentAmount, avgExpense, BigDecimal.ZERO, targetMonths, targetAmount, targetAmount,
                    EmergencyFundStatus.NO_EMERGENCY_FUND);
        }

        if (avgExpense.compareTo(BigDecimal.ZERO) == 0) {
            return new Result(currentAmount, avgExpense, null, targetMonths, BigDecimal.ZERO, BigDecimal.ZERO,
                    EmergencyFundStatus.ADEQUATE);
        }

        BigDecimal coverageMonths = currentAmount.divide(avgExpense, 2, RoundingMode.HALF_UP);
        BigDecimal targetAmount = avgExpense.multiply(BigDecimal.valueOf(targetMonths));
        BigDecimal gap = targetAmount.subtract(currentAmount).max(BigDecimal.ZERO);

        String status;
        if (coverageMonths.compareTo(BigDecimal.ONE) < 0) {
            status = EmergencyFundStatus.CRITICAL;
        } else if (coverageMonths.compareTo(BigDecimal.valueOf(targetMonths)) < 0) {
            status = EmergencyFundStatus.BUILDING;
        } else {
            status = EmergencyFundStatus.ADEQUATE;
        }

        return new Result(currentAmount, avgExpense, coverageMonths, targetMonths, targetAmount, gap, status);
    }
}
