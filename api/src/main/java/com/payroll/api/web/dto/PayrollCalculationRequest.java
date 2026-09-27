package com.payroll.api.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO for the payroll calculation endpoint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollCalculationRequest {

    @NotBlank(message = "employeeId is required")
    private String employeeId;

    @NotNull(message = "period is required")
    @Valid
    private PeriodRequest period;

    @NotNull(message = "employee is required")
    @Valid
    private EmployeeRequest employee;

    @NotNull(message = "attendance is required")
    @Valid
    private AttendanceRequest attendance;

    @Valid
    private List<ActivityRequest> activities;
}