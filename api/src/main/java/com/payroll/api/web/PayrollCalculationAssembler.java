package com.payroll.api.web;

import com.payroll.api.web.dto.ActivityRequest;
import com.payroll.api.web.dto.PayrollCalculationRequest;
import com.payroll.common.domain.ActivityRecord;
import com.payroll.common.domain.AttendanceRecord;
import com.payroll.common.domain.CalculationContext;
import com.payroll.common.domain.Employee;
import com.payroll.common.domain.PayPeriod;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Assembles the {@link CalculationContext} from the validated request, mapping
 * request data onto the attribute names declared in attributes.json. Business
 * facts are handed over as plain Java values; eligibility/formula rules decide
 * everything else.
 */
@Component
public class PayrollCalculationAssembler {

    private final PayrollRequestMapper mapper;

    public PayrollCalculationAssembler(PayrollRequestMapper mapper) {
        this.mapper = mapper;
    }

    public CalculationContext assemble(PayrollCalculationRequest request) {
        PayPeriod period = mapper.toPeriod(request.getPeriod());
        Employee employee = mapper.toEmployee(request.getEmployee());
        AttendanceRecord attendance = mapper.toAttendance(request.getAttendance());

        ActivityRecord activity = firstActivity(request);

        CalculationContext context = new CalculationContext()
                .setEmployeeId(request.getEmployeeId())
                .setPeriod(period)
                .setAttribute("hourlyRate", employee.getHourlyRate())
                .setAttribute("employeeGrade", employee.getGrade())
                .setAttribute("employmentType", employee.getEmploymentType() == null ? null : employee.getEmploymentType().name())
                .setAttribute("overtimeMultiplier", employee.getOvertimeMultiplier())
                .setAttribute("transportAllowance", employee.getTransportAllowance())
                .setAttribute("mealAllowance", employee.getMealAllowance())
                .setAttribute("providentFundRate", employee.getProvidentFundRate())
                .setAttribute("standardHours", attendance.getStandardHours())
                .setAttribute("overtimeHours", attendance.getOvertimeHours());

        if (activity != null) {
            context.setAttribute("activityType", activity.getActivityType());
            context.setAttribute("activityUnits", activity.getUnits());
            context.setAttribute("activityUnitRate", activity.getUnitRate());
        }
        return context;
    }

    private ActivityRecord firstActivity(PayrollCalculationRequest request) {
        List<ActivityRequest> activities = request.getActivities();
        if (activities == null || activities.isEmpty()) {
            return null;
        }
        return mapper.toActivity(activities.get(0));
    }
}