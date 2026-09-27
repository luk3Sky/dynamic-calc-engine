package com.payroll.api.web.dto;

import com.payroll.common.domain.EmploymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Employee master-data slice of the calculation request.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeRequest {

    @NotNull(message = "employee.grade is required")
    @Min(value = 1, message = "employee.grade must be >= 1")
    private Integer grade;

    @NotNull(message = "employee.employmentType is required")
    private EmploymentType employmentType;

    @DecimalMin(value = "0.00", message = "employee.hourlyRate must be >= 0")
    private BigDecimal hourlyRate;

    @DecimalMin(value = "0.00", message = "employee.providentFundRate must be >= 0")
    private BigDecimal providentFundRate;

    @DecimalMin(value = "0.00", message = "employee.transportAllowance must be >= 0")
    private BigDecimal transportAllowance;

    @DecimalMin(value = "0.00", message = "employee.mealAllowance must be >= 0")
    private BigDecimal mealAllowance;

    @DecimalMin(value = "0.00", message = "employee.overtimeMultiplier must be >= 0")
    private BigDecimal overtimeMultiplier;
}