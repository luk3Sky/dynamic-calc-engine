package com.payroll.rulesengine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payroll.common.config.ConfigValidationException;
import com.payroll.common.config.ConfigValidator;
import com.payroll.common.config.EligibilityRuleConfig;
import com.payroll.common.config.FormulaRuleConfig;
import com.payroll.common.config.JacksonConfigLoaderService;
import com.payroll.common.config.PayrollConfig;
import com.payroll.common.domain.CalculationContext;
import com.payroll.common.domain.PayPeriod;
import com.payroll.common.domain.PayrollResult;
import com.payroll.rulesengine.drools.DroolsEligibilityEngine;
import com.payroll.rulesengine.jeasy.WorkflowExecutor;
import com.payroll.rulesengine.orchestration.PayrollCalculationOrchestrator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves {@link ConfigRuntime#reload} atomically swaps in a new compilation
 * generation (Drools KieBase + jEasy rule sets) and that a subsequent
 * calculation through the orchestrator reflects the change — the exact
 * behaviour the admin console's "Apply to Runtime" depends on.
 */
class ConfigRuntimeTest {

    private ConfigRuntime runtime;
    private PayrollCalculationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        PayrollConfig initial = new JacksonConfigLoaderService().loadConfig();
        runtime = new ConfigRuntime(initial, new ConfigValidator());
        orchestrator = new PayrollCalculationOrchestrator(
                runtime, new DroolsEligibilityEngine(runtime), new WorkflowExecutor(runtime));
    }

    @Test
    void reloadSwapsFormulaRulesAndNextCalculationReflectsThem() {
        PayrollResult before = orchestrator.calculate(fullTimeContext());

        assertThat(before.getNetPay()).isEqualTo(new BigDecimal("1117.50"));

        // Double the overtime premium formula: 25.00 * 4 * 1.5 * 2 = 300.00
        PayrollConfig edited = copy(configWithFormulaReplaced(runtime.getConfig(), "RULE_OVERTIME_PAY",
                "hourlyRate * overtimeHours * overtimeMultiplier * 2"));
        runtime.reload(edited);

        PayrollResult after = orchestrator.calculate(fullTimeContext());

        // 1000.00 base + 300.00 overtime + 225.00 allowances = 1525.00 gross;
        // SLAB_2 10% -> 152.50 tax; PF 120.00 -> net 1252.50
        assertThat(after.getGrossPay()).isEqualTo(new BigDecimal("1525.00"));
        assertThat(after.getTotalDeductions()).isEqualTo(new BigDecimal("272.50"));
        assertThat(after.getNetPay()).isEqualTo(new BigDecimal("1252.50"));
    }

    @Test
    void reloadSwapsEligibilityRulesAndNextCalculationReflectsThem() {
        // Tighten overtime eligibility to grade >= 5; the grade-4 employee used
        // in the sample must no longer be classified as eligible.
        PayrollConfig edited = configWithEligibilityThreshold(runtime.getConfig(),
                "RULE_OVERTIME_ELIGIBLE", "employeeGrade", new BigDecimal("5"));
        runtime.reload(edited);

        PayrollResult after = orchestrator.calculate(fullTimeContext());

        // overtimeEligible never becomes true -> overtimePay stays at its 0 default;
        // gross 1225.00, SLAB_2 10% -> 122.50 tax, PF 120.00 -> net 982.50
        assertThat(after.getGrossPay()).isEqualTo(new BigDecimal("1225.00"));
        assertThat(after.getNetPay()).isEqualTo(new BigDecimal("982.50"));
    }

    @Test
    void reloadRejectsInvalidConfigAndKeepsPreviousGenerationActive() {
        PayrollConfig original = runtime.getConfig();
        PayrollConfig broken = configWithFormulaReplaced(runtime.getConfig(), "RULE_OVERTIME_PAY",
                "hourlyRate * overtimeHours * nonExistentAttribute");

        assertThatThrownBy(() -> runtime.reload(broken))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("nonExistentAttribute");

        assertThat(runtime.getConfig()).isEqualTo(original);
        assertThat(orchestrator.calculate(fullTimeContext()).getNetPay()).isEqualTo(new BigDecimal("1117.50"));
    }

    private PayrollConfig copy(PayrollConfig config) {
        return PayrollConfig.builder()
                .attributes(config.getAttributes())
                .eligibilityRules(config.getEligibilityRules())
                .formulaRules(config.getFormulaRules())
                .stages(config.getStages())
                .build();
    }

    private PayrollConfig configWithFormulaReplaced(PayrollConfig config, String ruleId, String formula) {
        List<FormulaRuleConfig> formulaRules = config.getFormulaRules().stream()
                .map(rule -> rule.getRuleId().equals(ruleId)
                        ? FormulaRuleConfig.builder()
                                .ruleId(rule.getRuleId())
                                .name(rule.getName())
                                .description(rule.getDescription())
                                .priority(rule.getPriority())
                                .condition(rule.getCondition())
                                .formula(formula)
                                .outputAttribute(rule.getOutputAttribute())
                                .componentType(rule.getComponentType())
                                .build()
                        : rule)
                .toList();
        return PayrollConfig.builder()
                .attributes(config.getAttributes())
                .eligibilityRules(config.getEligibilityRules())
                .formulaRules(formulaRules)
                .stages(config.getStages())
                .build();
    }

    private PayrollConfig configWithEligibilityThreshold(PayrollConfig config, String ruleId,
            String targetAttribute, BigDecimal threshold) {
        List<EligibilityRuleConfig> eligibilityRules = config.getEligibilityRules().stream()
                .map(rule -> rule.getRuleId().equals(ruleId)
                        ? EligibilityRuleConfig.builder()
                                .ruleId(rule.getRuleId())
                                .description(rule.getDescription())
                                .conditions(rule.getConditions().stream()
                                        .map(condition -> condition.getAttribute().equals(targetAttribute)
                                                ? com.payroll.common.config.ConditionConfig.builder()
                                                        .attribute(targetAttribute)
                                                        .operator(com.payroll.common.config.ConditionOperator.GREATER_THAN)
                                                        .value(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                                                                .numberNode(threshold))
                                                        .build()
                                                : condition)
                                        .toList())
                                .derivedFacts(rule.getDerivedFacts())
                                .build()
                        : rule)
                .toList();
        return PayrollConfig.builder()
                .attributes(config.getAttributes())
                .eligibilityRules(eligibilityRules)
                .formulaRules(config.getFormulaRules())
                .stages(config.getStages())
                .build();
    }

    private CalculationContext fullTimeContext() {
        return new CalculationContext()
                .setEmployeeId("EMP-1001")
                .setPeriod(PayPeriod.builder()
                        .startDate(LocalDate.of(2026, 9, 1))
                        .endDate(LocalDate.of(2026, 9, 30))
                        .build())
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
    }
}