package com.payroll.common.config;

import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Immutable bundle of the four configuration files, assembled and validated at
 * startup by {@link ConfigLoaderService}. Its getters are also the read-only
 * projection served to the future low-code UI.
 */
@Getter
@Builder
@AllArgsConstructor
public class PayrollConfig {

    private final List<AttributeConfig> attributes;
    private final List<EligibilityRuleConfig> eligibilityRules;
    private final List<FormulaRuleConfig> formulaRules;
    private final List<WorkflowStage> stages;

    public Optional<AttributeConfig> findAttribute(String id) {
        return attributes.stream().filter(a -> a.getId().equals(id)).findFirst();
    }

    public Optional<EligibilityRuleConfig> findEligibilityRule(String ruleId) {
        return eligibilityRules.stream().filter(r -> r.getRuleId().equals(ruleId)).findFirst();
    }

    public Optional<FormulaRuleConfig> findFormulaRule(String ruleId) {
        return formulaRules.stream().filter(r -> r.getRuleId().equals(ruleId)).findFirst();
    }
}