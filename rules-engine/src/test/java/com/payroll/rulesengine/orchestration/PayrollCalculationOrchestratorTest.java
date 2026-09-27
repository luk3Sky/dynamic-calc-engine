package com.payroll.rulesengine.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.payroll.common.config.ConfigLoaderService;
import com.payroll.common.config.ConfigValidator;
import com.payroll.common.config.JacksonConfigLoaderService;
import com.payroll.common.domain.CalculationContext;
import com.payroll.common.domain.PayComponent;
import com.payroll.common.domain.PayPeriod;
import com.payroll.common.domain.PayrollResult;
import com.payroll.rulesengine.config.ConfigRuntime;
import com.payroll.rulesengine.drools.DroolsEligibilityEngine;
import com.payroll.rulesengine.jeasy.WorkflowExecutor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * End-to-end proof that the Drools + jEasy chain produces the exact,
 * hand-computed PayrollResult for realistic sample employees (no Spring context
 * required).
 */
class PayrollCalculationOrchestratorTest {

    private static PayrollCalculationOrchestrator orchestrator;
    private static ConfigLoaderService configLoader;

    @BeforeAll
    static void setUp() {
        configLoader = new JacksonConfigLoaderService();
        ConfigRuntime runtime = new ConfigRuntime(configLoader.loadConfig(), new ConfigValidator());
        orchestrator = new PayrollCalculationOrchestrator(
                runtime,
                new DroolsEligibilityEngine(runtime),
                new WorkflowExecutor(runtime));
    }

    @Test
    void fullTimeEmployeeWithOvertimeCalculatesExactNetPay() {
        CalculationContext ctx = context("EMP-1001")
                .setAttribute("hourlyRate", new BigDecimal("25.00"))
                .setAttribute("standardHours", new BigDecimal("40"))
                .setAttribute("overtimeHours", new BigDecimal("4"))
                .setAttribute("overtimeMultiplier", new BigDecimal("1.5"))
                .setAttribute("employeeGrade", 4)
                .setAttribute("employmentType", "FULL_TIME")
                .setAttribute("activityType", "DELIVERY")
                .setAttribute("activityUnits", new BigDecimal("0"))
                .setAttribute("transportAllowance", new BigDecimal("150.00"))
                .setAttribute("mealAllowance", new BigDecimal("75.00"))
                .setAttribute("providentFundRate", new BigDecimal("0.12"));

        PayrollResult result = orchestrator.calculate(ctx);

        // BASE_PAY 25.00*40=1000.00; OVERTIME 25.00*4*1.5=150.00; ALLOWANCES
        // 150.00+75.00=225.00 -> GROSS 1375.00; SLAB_2 (10%) -> TAX 137.50;
        // PF 1000.00*0.12=120.00 -> DEDUCTIONS 257.50 -> NET 1117.50
        assertThat(result.getGrossPay()).isEqualTo(new BigDecimal("1375.00"));
        assertThat(result.getTotalDeductions()).isEqualTo(new BigDecimal("257.50"));
        assertThat(result.getNetPay()).isEqualTo(new BigDecimal("1117.50"));

        assertThat(result.getComponents()).extracting(PayComponent::getSourceRule)
                .containsExactly("RULE_BASE_PAY", "RULE_OVERTIME_PAY",
                        "RULE_TRANSPORT_ALLOWANCE", "RULE_MEAL_ALLOWANCE",
                        "RULE_INCOME_TAX", "RULE_PROVIDENT_FUND");

        List<String> trace = result.getCalculationTrace();
        assertThat(trace).isNotEmpty();
        // the second Drools pass must classify the slab after GROSS and before INCOME_TAX
        assertThat(trace.indexOf(traceLine(trace, "RULE_GROSS_TOTAL")))
                .isLessThan(trace.indexOf(traceLine(trace, "RULE_TAX_SLAB_MID")));
        assertThat(trace.indexOf(traceLine(trace, "RULE_TAX_SLAB_MID")))
                .isLessThan(trace.indexOf(traceLine(trace, "RULE_INCOME_TAX")));
        assertThat(ctx.getAttribute("taxSlab")).isEqualTo("SLAB_2");
    }

    @Test
    void activityContractorCalculatesExactNetPay() {
        CalculationContext ctx = context("EMP-3003")
                .setAttribute("hourlyRate", new BigDecimal("0"))
                .setAttribute("standardHours", new BigDecimal("0"))
                .setAttribute("employeeGrade", 1)
                .setAttribute("employmentType", "CONTRACT")
                .setAttribute("activityType", "PIECEWORK")
                .setAttribute("activityUnits", new BigDecimal("500"))
                .setAttribute("activityUnitRate", new BigDecimal("2.50"))
                .setAttribute("providentFundRate", new BigDecimal("0"));

        PayrollResult result = orchestrator.calculate(ctx);

        // ACTIVITY 500*2.50=1250.00; GROSS 1250.00; SLAB_2 (10%) -> TAX 125.00;
        // no PF (contractor) -> NET 1125.00
        assertThat(result.getGrossPay()).isEqualTo(new BigDecimal("1250.00"));
        assertThat(result.getTotalDeductions()).isEqualTo(new BigDecimal("125.00"));
        assertThat(result.getNetPay()).isEqualTo(new BigDecimal("1125.00"));

        assertThat(result.getComponents()).extracting(PayComponent::getSourceRule)
                .containsExactly("RULE_ACTIVITY_PAY", "RULE_INCOME_TAX");
    }

    private CalculationContext context(String employeeId) {
        return new CalculationContext()
                .setEmployeeId(employeeId)
                .setPeriod(PayPeriod.builder()
                        .startDate(LocalDate.of(2026, 9, 1))
                        .endDate(LocalDate.of(2026, 9, 30))
                        .build());
    }

    private String traceLine(List<String> trace, String ruleId) {
        return trace.stream()
                .filter(line -> line.startsWith(ruleId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("trace missing " + ruleId + ": " + trace));
    }
}