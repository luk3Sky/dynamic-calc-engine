package com.payroll.common.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One activity-based work entry (DELIVERY / SALES_UNIT / PIECEWORK).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityRecord {

    private String activityType;
    private BigDecimal units;
    private BigDecimal unitRate;
}