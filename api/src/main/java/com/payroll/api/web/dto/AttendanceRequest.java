package com.payroll.api.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Attendance slice of the calculation request.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRequest {

    @NotNull(message = "attendance.standardHours is required")
    @DecimalMin(value = "0.00", message = "attendance.standardHours must be >= 0")
    private BigDecimal standardHours;

    @NotNull(message = "attendance.overtimeHours is required")
    @DecimalMin(value = "0.00", message = "attendance.overtimeHours must be >= 0")
    private BigDecimal overtimeHours;
}