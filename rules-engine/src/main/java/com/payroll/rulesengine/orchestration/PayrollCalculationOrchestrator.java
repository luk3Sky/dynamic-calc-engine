package com.payroll.rulesengine.orchestration;

import com.payroll.common.config.ConfigLoaderService;
import com.payroll.common.config.EngineType;
import com.payroll.common.config.WorkflowStage;
import com.payroll.common.domain.CalculationContext;
import com.payroll.common.domain.PayrollResult;
import com.payroll.rulesengine.drools.DroolsEligibilityEngine;
import com.payroll.rulesengine.jeasy.WorkflowExecutor;
import java.math.BigDecimal;

/**
 * Chains the two engines for a single calculation:
 *
 * <ol>
 *   <li>pre-seeds config defaults onto the context,</li>
 *   <li>iterates workflow.json stages in order, dispatching ELIGIBILITY stages
 *       to Drools and FORMULA stages to jEasy,</li>
 *   <li>maps the final attributes into a {@link PayrollResult}.</li>
 * </ol>
 *
 * <p>Drools and jEasy never see each other; this orchestrator is the only
 * component that knows both. Because the workflow is config-driven, the second
 * lightweight Drools pass (TAX_SLAB_DETERMINATION) is just another stage that
 * runs after GROSS_TOTAL, which is how the slab-depends-on-gross circular
 * dependency is resolved without ad-hoc Java logic.
 */
public class PayrollCalculationOrchestrator {

    private final ConfigLoaderService configLoader;
    private final DroolsEligibilityEngine droolsEngine;
    private final WorkflowExecutor workflowExecutor;

    public PayrollCalculationOrchestrator(
            ConfigLoaderService configLoader,
            DroolsEligibilityEngine droolsEngine,
            WorkflowExecutor workflowExecutor) {
        this.configLoader = configLoader;
        this.droolsEngine = droolsEngine;
        this.workflowExecutor = workflowExecutor;
    }

    public PayrollResult calculate(CalculationContext context) {
        ContextDefaults.apply(context, configLoader.loadConfig());

        for (WorkflowStage stage : configLoader.getWorkflowStages()) {
            if (stage.getEngine() == EngineType.ELIGIBILITY) {
                droolsEngine.runRules(context, stage.getRuleIds());
            } else {
                workflowExecutor.executeStage(context, stage);
            }
        }

        return toResult(context);
    }

    private PayrollResult toResult(CalculationContext context) {
        return PayrollResult.builder()
                .employeeId(context.getEmployeeId())
                .period(context.getPeriod())
                .components(context.getPayComponents())
                .grossPay(decimal(context, "grossTotal"))
                .totalDeductions(decimal(context, "totalDeductions"))
                .netPay(decimal(context, "netPay"))
                .calculationTrace(context.getCalculationTrace())
                .build();
    }

    private BigDecimal decimal(CalculationContext context, String attribute) {
        Object value = context.getAttribute(attribute);
        return value instanceof Number number
                ? new BigDecimal(number.toString()).setScale(2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
    }
}