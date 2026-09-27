package com.payroll.api.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One activity work entry of the calculation request.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityRequest {

    @NotBlank(message = "activities[].activityType is required")
    private String activityType;

    @NotNull(message = "activities[].units is required")
    @DecimalMin(value = "0.00", message = "activities[].units must be >= 0")
    private BigDecimal units;

    @DecimalMin(value = "0.00", message = "activities[].unitRate must be >= 0")
    private BigDecimal unitRate;
}