package com.payroll.common.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An itemized pay line, e.g. BASE_PAY (EARNING) or INCOME_TAX (DEDUCTION).
 * {@code sourceRule} is the ruleId in formula-rules.json that produced it, so
 * every number in the result traces back to the JSON configuration.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayComponent {

    private String name;
    private ComponentType type;
    private BigDecimal amount;
    private String sourceRule;
}