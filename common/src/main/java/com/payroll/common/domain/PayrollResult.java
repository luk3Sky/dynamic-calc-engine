package com.payroll.common.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Final, fully itemized result of a payroll calculation. All monetary fields
 * are {@link BigDecimal}, never double/float.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollResult {

    private String employeeId;
    private PayPeriod period;
    private List<PayComponent> components = new ArrayList<>();
    private BigDecimal grossPay;
    private BigDecimal totalDeductions;
    private BigDecimal netPay;
    private List<String> calculationTrace = new ArrayList<>();
}