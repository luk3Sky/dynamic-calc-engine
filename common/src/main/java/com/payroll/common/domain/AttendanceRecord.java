package com.payroll.common.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Time attendance for the period. Hours are {@link BigDecimal} so that any
 * fractional overtime remains exact (no double/float anywhere in this codebase).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord {

    private BigDecimal standardHours;
    private BigDecimal overtimeHours;
}