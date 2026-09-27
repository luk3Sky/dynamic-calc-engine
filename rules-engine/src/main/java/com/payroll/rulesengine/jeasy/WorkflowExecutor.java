package com.payroll.rulesengine.jeasy;

import com.payroll.common.config.ConfigLoaderService;
import com.payroll.common.config.FormulaRuleConfig;
import com.payroll.common.config.WorkflowStage;
import com.payroll.common.domain.CalculationContext;
import com.payroll.rulesengine.orchestration.FormulaEvaluationException;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rule;
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
 * <p>Stages are fired individually and in workflow.json order by the
 * orchestrator, which is what makes later stages see earlier stages' outputs
 * (e.g. GROSS_TOTAL -> TAX_SLAB -> STATUTORY_DEDUCTIONS).
 */
public class WorkflowExecutor {

    private final ConfigLoaderService configLoader;
    private final FormulaRuleFactory ruleFactory;
    private final RulesEngine rulesEngine = new DefaultRulesEngine();

    public WorkflowExecutor(ConfigLoaderService configLoader) {
        this(configLoader, new FormulaRuleFactory());
    }

    public WorkflowExecutor(ConfigLoaderService configLoader, FormulaRuleFactory ruleFactory) {
        this.configLoader = configLoader;
        this.ruleFactory = ruleFactory;
    }

    public CalculationContext executeStage(CalculationContext context, WorkflowStage stage) {
        Facts facts = new Facts();
        facts.put("context", context);

        Rules rules = new Rules();
        for (String ruleId : stage.getRuleIds()) {
            FormulaRuleConfig config = configLoader.formulaRule(ruleId)
                    .orElseThrow(() -> new FormulaEvaluationException(
                            "Formula stage [" + stage.getName() + "] references unknown ruleId", ruleId));
            Rule rule = ruleFactory.createRule(config);
            rules.register(rule);
        }

        rulesEngine.fire(rules, facts);
        return context;
    }
}