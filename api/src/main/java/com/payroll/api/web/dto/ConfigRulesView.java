package com.payroll.api.web.dto;

import com.payroll.common.config.EligibilityRuleConfig;
import com.payroll.common.config.FormulaRuleConfig;
import java.util.List;

/**
 * Read-only projection of the loaded rules, served for future low-code UI
 * introspection.
 */
public record ConfigRulesView(List<EligibilityRuleConfig> eligibilityRules, List<FormulaRuleConfig> formulaRules) {
}