package com.payroll.rulesengine.config;

import com.payroll.common.config.PayrollConfig;
import com.payroll.common.config.WorkflowStage;
import com.payroll.rulesengine.orchestration.FormulaEvaluationException;
import java.util.Map;
import org.jeasy.rules.api.Rules;
import org.kie.api.runtime.KieContainer;

/**
 * Immutable, atomically-swappable snapshot of everything the engines need for a
 * single configuration generation: the validated {@link PayrollConfig}, the
 * Drools {@link KieContainer} compiled from its eligibility rules, and the
 * prebuilt jEasy {@link Rules} set for each formula stage.
 *
 * <p>Instances are produced by {@link ConfigRuntime} and swapped via an
 * {@code AtomicReference}, so a concurrent calculation always observes one
 * complete, internally-consistent generation — never a half-reloaded one.
 */
public class CompiledConfig {

    private final PayrollConfig config;
    private final KieContainer kieContainer;
    private final Map<String, Rules> formulaStageRules;

    public CompiledConfig(PayrollConfig config, KieContainer kieContainer, Map<String, Rules> formulaStageRules) {
        this.config = config;
        this.kieContainer = kieContainer;
        this.formulaStageRules = Map.copyOf(formulaStageRules);
    }

    public PayrollConfig getConfig() {
        return config;
    }

    public KieContainer getKieContainer() {
        return kieContainer;
    }

    /**
     * Returns the prebuilt jEasy rules for the given formula stage, or throws
     * when the stage is not a compiled formula stage.
     */
    public Rules formulaRules(WorkflowStage stage) {
        Rules rules = formulaStageRules.get(stage.getName());
        if (rules == null) {
            throw new FormulaEvaluationException(
                    "Formula stage [" + stage.getName() + "] has no compiled jEasy rules", stage.getName());
        }
        return rules;
    }
}