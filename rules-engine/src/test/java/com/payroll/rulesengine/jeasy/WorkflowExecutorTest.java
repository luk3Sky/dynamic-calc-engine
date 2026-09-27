package com.payroll.rulesengine.jeasy;

import static org.assertj.core.api.Assertions.assertThat;

import com.payroll.common.config.ConfigLoaderService;
import com.payroll.common.config.JacksonConfigLoaderService;
import com.payroll.common.config.WorkflowStage;
import com.payroll.common.domain.CalculationContext;
import com.payroll.rulesengine.orchestration.ContextDefaults;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Proves a multi-stage jEasy run (BASE_PAY -> OVERTIME -> GROSS_TOTAL)
 * produces mathematically exact BigDecimal results and a non-empty,
 * correctly ordered calculation trace.
 */
class WorkflowExecutorTest {

    private static ConfigLoaderService configLoader;
    private static WorkflowExecutor executor;

    @BeforeAll
    static void setUp() {
        configLoader = new JacksonConfigLoaderService();
        executor = new WorkflowExecutor(configLoader);
    }

    @Test
    void multiStageRunProducesExactValuesAndOrderedTrace() {
        CalculationContext ctx = ContextDefaults.apply(
                new CalculationContext()
                        .setAttribute("hourlyRate", new BigDecimal("25.00"))
                        .setAttribute("standardHours", new BigDecimal("40"))
                        .setAttribute("overtimeHours", new BigDecimal("4"))
                        .setAttribute("overtimeMultiplier", new BigDecimal("1.5"))
                        .setAttribute("overtimeEligible", true),
                configLoader.loadConfig());

        executor.executeStage(ctx, stage("BASE_PAY"));
        executor.executeStage(ctx, stage("OVERTIME"));
        executor.executeStage(ctx, stage("GROSS_TOTAL"));

        // BASE_PAY: 25.00 * 40 = 1000.00
        assertThat(ctx.getAttribute("basePay")).isEqualTo(new BigDecimal("1000.00"));
        // OVERTIME_PAY: 25.00 * 4 * 1.5 = 150.00
        assertThat(ctx.getAttribute("overtimePay")).isEqualTo(new BigDecimal("150.00"));
        // GROSS_TOTAL: 1000.00 + 150.00 + 0 + 0 = 1150.00
        assertThat(ctx.getAttribute("grossTotal")).isEqualTo(new BigDecimal("1150.00"));

        assertThat(ctx.getCalculationTrace()).isNotEmpty();
        List<String> trace = ctx.getCalculationTrace();
        int basePayIndex = indexOfRule(trace, "RULE_BASE_PAY");
        int overtimeIndex = indexOfRule(trace, "RULE_OVERTIME_PAY");
        int grossIndex = indexOfRule(trace, "RULE_GROSS_TOTAL");
        assertThat(basePayIndex).isLessThan(overtimeIndex);
        assertThat(overtimeIndex).isLessThan(grossIndex);

        assertThat(trace.get(overtimeIndex))
                .isEqualTo("RULE_OVERTIME_PAY: hourlyRate(25.00) * overtimeHours(4) * overtimeMultiplier(1.5) = 150.00");
    }

    @Test
    void conditionalRuleDoesNotFireWhenIneligible() {
        CalculationContext ctx = ContextDefaults.apply(
                new CalculationContext()
                        .setAttribute("hourlyRate", new BigDecimal("20.00"))
                        .setAttribute("standardHours", new BigDecimal("20"))
                        .setAttribute("overtimeHours", new BigDecimal("5"))
                        .setAttribute("overtimeMultiplier", new BigDecimal("1.5"))
                        .setAttribute("overtimeEligible", false),
                configLoader.loadConfig());

        executor.executeStage(ctx, stage("BASE_PAY"));
        executor.executeStage(ctx, stage("OVERTIME"));
        executor.executeStage(ctx, stage("GROSS_TOTAL"));

        assertThat(ctx.getAttribute("basePay")).isEqualTo(new BigDecimal("400.00"));
        // inapplicable components stay at their attributes.json defaultValue (0)
        assertThat(ctx.getAttribute("overtimePay")).isEqualTo(new BigDecimal("0"));
        // 400.00 + 0 + 0 = 400.00
        assertThat(ctx.getAttribute("grossTotal")).isEqualTo(new BigDecimal("400.00"));
    }

    private WorkflowStage stage(String name) {
        return configLoader.getWorkflowStages().stream()
                .filter(s -> s.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("workflow stage not found: " + name));
    }

    private int indexOfRule(List<String> trace, String ruleId) {
        for (int i = 0; i < trace.size(); i++) {
            if (trace.get(i).startsWith(ruleId)) {
                return i;
            }
        }
        throw new AssertionError("trace does not contain " + ruleId + ": " + trace);
    }
}