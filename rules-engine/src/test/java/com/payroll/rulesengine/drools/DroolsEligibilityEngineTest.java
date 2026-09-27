package com.payroll.rulesengine.drools;

import static org.assertj.core.api.Assertions.assertThat;

import com.payroll.common.config.ConfigValidator;
import com.payroll.common.config.JacksonConfigLoaderService;
import com.payroll.common.domain.CalculationContext;
import com.payroll.rulesengine.config.ConfigRuntime;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Proves generated DRL rules evaluate at least three distinct
 * condition/operator combinations from the sample eligibility-rules.json and
 * correctly mutate the CalculationContext.
 */
class DroolsEligibilityEngineTest {

    private static DroolsEligibilityEngine engine;

    @BeforeAll
    static void compileOnce() {
        ConfigRuntime runtime = new ConfigRuntime(
                new JacksonConfigLoaderService().loadConfig(), new ConfigValidator());
        engine = new DroolsEligibilityEngine(runtime);
    }

    @Test
    void fullTimeEmployeeAtGrade4BecomesOvertimeEligible() {
        // EQUALS + GREATER_THAN combination
        CalculationContext ctx = new CalculationContext()
                .setAttribute("employmentType", "FULL_TIME")
                .setAttribute("employeeGrade", 4);

        engine.runRules(ctx);

        assertThat(ctx.getAttribute("overtimeEligible")).isEqualTo(true);
        assertThat(ctx.getCalculationTrace())
                .anyMatch(line -> line.startsWith("RULE_OVERTIME_ELIGIBLE"));
    }

    @Test
    void activityWorkerWithUnitsBecomesActivityBonusEligible() {
        // IN + GREATER_THAN combination
        CalculationContext ctx = new CalculationContext()
                .setAttribute("activityType", "DELIVERY")
                .setAttribute("activityUnits", 5);

        engine.runRules(ctx);

        assertThat(ctx.getAttribute("activityBonusEligible")).isEqualTo(true);
    }

    @Test
    void nonEligiblePartTimeEmployeeStaysFalse() {
        CalculationContext ctx = new CalculationContext()
                .setAttribute("employmentType", "PART_TIME")
                .setAttribute("employeeGrade", 2);

        engine.runRules(ctx);

        assertThat(ctx.getAttribute("overtimeEligible")).isNull();
    }

    @Test
    void grossBetweenBoundsClassifiesMidSlab() {
        // BETWEEN combination (inclusive [1200.01, 4000])
        CalculationContext ctx = new CalculationContext()
                .setAttribute("grossTotal", new BigDecimal("1375.00"));

        engine.runRules(ctx);

        assertThat(ctx.getAttribute("taxSlab")).isEqualTo("SLAB_2");
        assertThat(ctx.getAttribute("incomeTaxRate")).isEqualTo(new BigDecimal("0.10"));
    }

    @Test
    void grossAboveUpperBoundClassifiesHighSlab() {
        // GREATER_THAN combination
        CalculationContext ctx = new CalculationContext()
                .setAttribute("grossTotal", new BigDecimal("5000.00"));

        engine.runRules(ctx);

        assertThat(ctx.getAttribute("taxSlab")).isEqualTo("SLAB_3");
        assertThat(ctx.getAttribute("incomeTaxRate")).isEqualTo(new BigDecimal("0.20"));
    }

    @Test
    void agendaFilterRunsOnlyRequestedRules() {
        CalculationContext ctx = new CalculationContext()
                .setAttribute("employmentType", "FULL_TIME")
                .setAttribute("employeeGrade", 4)
                .setAttribute("grossTotal", new BigDecimal("5000.00"));

        engine.runRules(ctx, List.of("RULE_TAX_SLAB_HIGH"));

        assertThat(ctx.getAttribute("taxSlab")).isEqualTo("SLAB_3");
        assertThat(ctx.getAttribute("overtimeEligible")).isNull();
    }

    @Test
    void evaluatorSupportsAllOperatorsDirectly() {
        assertThat(EligibilityConditionEvaluator.matches(
                new CalculationContext().setAttribute("grade", 2),
                "grade", "LESS_THAN", 3)).isTrue();
        assertThat(EligibilityConditionEvaluator.matches(
                new CalculationContext().setAttribute("type", "PART_TIME"),
                "type", "NOT_EQUALS", "FULL_TIME")).isTrue();
        assertThat(EligibilityConditionEvaluator.matches(
                new CalculationContext().setAttribute("type", "SALES_UNIT"),
                "type", "IN", List.of("DELIVERY", "SALES_UNIT", "PIECEWORK"))).isTrue();
        assertThat(EligibilityConditionEvaluator.matches(
                new CalculationContext().setAttribute("units", 5),
                "units", "BETWEEN", List.of(1, 10))).isTrue();
    }
}