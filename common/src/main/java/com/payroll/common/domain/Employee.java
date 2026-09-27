package com.payroll.common.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Employee master data. All monetary and rate fields are {@link BigDecimal};
 * floating point types are never used for money anywhere in this codebase.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee {

    private String id;
    private Integer grade;
    private EmploymentType employmentType;
    private BigDecimal hourlyRate;
    private BigDecimal providentFundRate;
    private BigDecimal transportAllowance;
    private BigDecimal mealAllowance;
    private BigDecimal overtimeMultiplier;
}