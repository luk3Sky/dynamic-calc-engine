package com.payroll.rulesengine.config;

import com.payroll.common.config.ConfigValidationException;
import com.payroll.common.config.ConfigValidator;
import com.payroll.common.config.EngineType;
import com.payroll.common.config.FormulaRuleConfig;
import com.payroll.common.config.PayrollConfig;
import com.payroll.common.config.ValidationResult;
import com.payroll.common.config.WorkflowStage;
import com.payroll.rulesengine.drools.DroolsKieBuilder;
import com.payroll.rulesengine.jeasy.FormulaRuleFactory;
import com.payroll.rulesengine.orchestration.FormulaEvaluationException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jeasy.rules.api.Rules;
import org.kie.api.runtime.KieContainer;

/**
 * Thread-safe, mutable holder for the currently active configuration generation
 * ({@link CompiledConfig}). Both the calculation orchestrator and the admin
 * console read from and write to this single instance, making it the one source
 * of truth for "what config is live right now".
 *
 * <p>It replaces the original assumption that the Drools KieBase is compiled
 * once and immutable for the application's lifetime: {@link #reload(PayrollConfig)}
 * recompiles the KieBase and rebuilds the jEasy rule sets, then atomically swaps
 * the new generation in via an {@link AtomicReference}. A concurrent calculation
 * either sees the old complete generation or the new complete generation, never
 * a mixture.
 *
 * <p>Every reload re-runs {@link ConfigValidator}; an invalid config is rejected
 * with {@link ConfigValidationException} and the previous generation stays
 * active. Validation failure therefore hard-blocks both "Apply to Runtime" and
 * "Save to File".
 */
public class ConfigRuntime {

    private final ConfigValidator validator;
    private final AtomicReference<CompiledConfig> current;

    public ConfigRuntime(PayrollConfig config, ConfigValidator validator) {
        this.validator = validator;
        this.current = new AtomicReference<>(compile(config));
    }

    /** Returns the currently active, atomically-consistent generation. */
    public CompiledConfig get() {
        return current.get();
    }

    /** Returns the currently active raw config (attributes, rules, stages). */
    public PayrollConfig getConfig() {
        return current.get().getConfig();
    }

    /**
     * Validates, recompiles (Drools KieBase + jEasy rule sets) and atomically
     * activates the given config. Throws {@link ConfigValidationException} and
     * leaves the previous generation active when the config is invalid.
     */
    public CompiledConfig reload(PayrollConfig config) {
        CompiledConfig compiled = compile(config);
        current.set(compiled);
        return compiled;
    }

    private CompiledConfig compile(PayrollConfig config) {
        ValidationResult result = validator.validate(config);
        if (!result.isValid()) {
            throw new ConfigValidationException(result.summary());
        }
        KieContainer kieContainer = DroolsKieBuilder.compile(config.getEligibilityRules());
        Map<String, Rules> stageRules = new HashMap<>();
        for (WorkflowStage stage : config.getStages()) {
            if (stage.getEngine() == EngineType.FORMULA) {
                stageRules.put(stage.getName(), buildStageRules(stage, config));
            }
        }
        return new CompiledConfig(config, kieContainer, stageRules);
    }

    private Rules buildStageRules(WorkflowStage stage, PayrollConfig config) {
        Rules rules = new Rules();
        FormulaRuleFactory ruleFactory = new FormulaRuleFactory();
        for (String ruleId : stage.getRuleIds()) {
            FormulaRuleConfig rule = config.findFormulaRule(ruleId)
                    .orElseThrow(() -> new FormulaEvaluationException(
                            "Formula stage [" + stage.getName() + "] references unknown ruleId", ruleId));
            rules.register(ruleFactory.createRule(rule));
        }
        return rules;
    }
}