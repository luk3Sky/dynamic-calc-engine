package com.payroll.common.config;

import java.util.List;
import java.util.Optional;

/**
 * Loads and validates the four JSON configuration files (attributes.json,
 * eligibility-rules.json, formula-rules.json, workflow.json) from a
 * configurable location and exposes the compiled, validated configuration.
 *
 * <p>Implementations must fail fast at load time when the configuration is
 * internally inconsistent (unknown ruleId in a workflow stage, undeclared
 * attribute reference, ...) rather than deferring the error to request time.
 */
public interface ConfigLoaderService {

    /** Returns the loaded (and validated) configuration. Cached by implementations. */
    PayrollConfig loadConfig();

    /**
     * Re-reads and re-validates the configuration from its source location and
     * returns the fresh config. Implementations that cache (such as the
     * Jackson loader) must invalidate their cache. Used by the admin console to
     * discard unsaved in-memory edits by reloading from disk.
     */
    default PayrollConfig reload() {
        return loadConfig();
    }

    default List<AttributeConfig> getAttributes() {
        return loadConfig().getAttributes();
    }

    default List<EligibilityRuleConfig> getEligibilityRules() {
        return loadConfig().getEligibilityRules();
    }

    default List<FormulaRuleConfig> getFormulaRules() {
        return loadConfig().getFormulaRules();
    }

    default List<WorkflowStage> getWorkflowStages() {
        return loadConfig().getStages();
    }

    default Optional<AttributeConfig> attribute(String id) {
        return loadConfig().findAttribute(id);
    }

    default Optional<FormulaRuleConfig> formulaRule(String ruleId) {
        return loadConfig().findFormulaRule(ruleId);
    }

    default Optional<EligibilityRuleConfig> eligibilityRule(String ruleId) {
        return loadConfig().findEligibilityRule(ruleId);
    }
}