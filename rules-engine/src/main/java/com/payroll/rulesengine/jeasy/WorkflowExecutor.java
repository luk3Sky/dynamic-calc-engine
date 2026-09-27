package com.payroll.rulesengine.jeasy;

import com.payroll.common.config.WorkflowStage;
import com.payroll.common.domain.CalculationContext;
import com.payroll.rulesengine.config.ConfigRuntime;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.jeasy.rules.core.DefaultRulesEngine;

/**
 * Executes one formula stage of workflow.json with jEasy.
 *
 * <p>easy-rules 4.x removed rule groups (UnitRuleGroup / ActivationRuleGroup),
 * so stage semantics are expressed as follows: SEQUENTIAL stages rely on rule
 * priority inside the stage's {@link Rules} set -- DefaultRulesEngine evaluates
 * the priority-sorted rules in order, so each rule's outputs are visible to the
 * next rule in the same stage; PARALLEL stages only guarantee that the rules
 * are independent (no ordering contract).
 *
 * <p>The stage's {@link Rules} set is prebuilt by {@link ConfigRuntime} at
 * startup and on every reload, so an admin "Apply to Runtime" immediately makes
 * the next stage execution here use the new formulas. Stages are fired
 * individually and in workflow.json order by the orchestrator, which is what
 * makes later stages see earlier stages' outputs (e.g. GROSS_TOTAL ->
 * TAX_SLAB -> STATUTORY_DEDUCTIONS).
 */
public class WorkflowExecutor {

    private final ConfigRuntime runtime;
    private final RulesEngine rulesEngine = new DefaultRulesEngine();

    public WorkflowExecutor(ConfigRuntime runtime) {
        this.runtime = runtime;
    }

    public CalculationContext executeStage(CalculationContext context, WorkflowStage stage) {
        Facts facts = new Facts();
        facts.put("context", context);

        Rules rules = runtime.get().formulaRules(stage);
        rulesEngine.fire(rules, facts);
        return context;
    }
}