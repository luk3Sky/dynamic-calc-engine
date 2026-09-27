package com.payroll.api.web;

import com.payroll.api.web.dto.ActivityRequest;
import com.payroll.api.web.dto.AttendanceRequest;
import com.payroll.api.web.dto.EmployeeRequest;
import com.payroll.api.web.dto.PeriodRequest;
import com.payroll.common.domain.ActivityRecord;
import com.payroll.common.domain.AttendanceRecord;
import com.payroll.common.domain.Employee;
import com.payroll.common.domain.PayPeriod;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * Maps the API request DTOs onto the domain models.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PayrollRequestMapper {

    PayPeriod toPeriod(PeriodRequest period);

    Employee toEmployee(EmployeeRequest employee);

    AttendanceRecord toAttendance(AttendanceRequest attendance);

    ActivityRecord toActivity(ActivityRequest activity);
}