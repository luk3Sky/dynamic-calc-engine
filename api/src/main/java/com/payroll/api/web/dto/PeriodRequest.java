package com.payroll.api.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pay period of the calculation request.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodRequest {

    @NotNull(message = "period.startDate is required")
    private LocalDate startDate;

    @NotNull(message = "period.endDate is required")
    private LocalDate endDate;

    @AssertTrue(message = "period.startDate must not be after period.endDate")
    public boolean isRangeValid() {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }
}