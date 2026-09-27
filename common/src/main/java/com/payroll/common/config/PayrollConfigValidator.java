package com.payroll.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cross-file structural validation of the payroll JSON configuration. Runs once
 * at load time so that internally inconsistent config fails fast at startup
 * with actionable messages naming the offending ruleId / attribute / stage.
 */
public final class PayrollConfigValidator {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern STRING_LITERAL = Pattern.compile("'[^']*'|\"[^\"]*\"");
    private static final Set<String> MVEL_LITERALS = Set.of(
            "true", "false", "null", "new", "if", "else", "for", "while", "do",
            "return", "def", "function", "import", "as", "in", "is", "not",
            "and", "or", "instanceof", "switch", "case", "default", "try",
            "catch", "finally", "throw", "this", "break", "continue");

    private PayrollConfigValidator() {
    }

    /**
     * Validates the bundled configuration and throws {@link ConfigValidationException}
     * listing every detected inconsistency.
     */
    public static void validate(PayrollConfig config) {
        List<String> errors = new ArrayList<>();

        Set<String> attributeIds = new HashSet<>();
        for (AttributeConfig attr : config.getAttributes()) {
            if (attr.getId() == null || attr.getId().isBlank()) {
                errors.add("attributes.json contains an attribute without an id");
            } else if (!attributeIds.add(attr.getId())) {
                errors.add("duplicate attribute id [" + attr.getId() + "] in attributes.json");
            }
        }

        Set<String> eligibilityRuleIds = new HashSet<>();
        for (EligibilityRuleConfig rule : config.getEligibilityRules()) {
            if (rule.getRuleId() == null || rule.getRuleId().isBlank()) {
                errors.add("eligibility-rules.json contains a rule without a ruleId");
                continue;
            }
            if (!eligibilityRuleIds.add(rule.getRuleId())) {
                errors.add("duplicate eligibility ruleId [" + rule.getRuleId() + "]");
            }
            if (rule.getConditions() == null || rule.getConditions().isEmpty()) {
                errors.add("eligibility rule [" + rule.getRuleId() + "] has no conditions");
            } else {
                int i = 1;
                for (ConditionConfig c : rule.getConditions()) {
                    if (c.getAttribute() == null || !attributeIds.contains(c.getAttribute())) {
                        errors.add("eligibility rule [" + rule.getRuleId() + "] condition #" + i
                                + " references attribute [" + c.getAttribute()
                                + "] which is not declared in attributes.json");
                    }
                    if (c.getOperator() == ConditionOperator.BETWEEN
                            && (c.getValue() == null || !c.getValue().isArray() || c.getValue().size() != 2)) {
                        errors.add("eligibility rule [" + rule.getRuleId() + "] condition #" + i
                                + " uses BETWEEN but value is not a two-element array");
                    }
                    if (c.getOperator() == ConditionOperator.IN
                            && (c.getValue() == null || !c.getValue().isArray())) {
                        errors.add("eligibility rule [" + rule.getRuleId() + "] condition #" + i
                                + " uses IN but value is not an array");
                    }
                    i++;
                }
            }
            if (rule.getDerivedFacts() == null || rule.getDerivedFacts().isEmpty()) {
                errors.add("eligibility rule [" + rule.getRuleId() + "] has no derived facts");
            } else {
                for (String key : rule.getDerivedFacts().keySet()) {
                    if (!attributeIds.contains(key)) {
                        errors.add("eligibility rule [" + rule.getRuleId() + "] derives attribute ["
                                + key + "] which is not declared in attributes.json");
                    }
                }
            }
        }

        Set<String> formulaRuleIds = new HashSet<>();
        for (FormulaRuleConfig rule : config.getFormulaRules()) {
            if (rule.getRuleId() == null || rule.getRuleId().isBlank()) {
                errors.add("formula-rules.json contains a rule without a ruleId");
                continue;
            }
            if (!formulaRuleIds.add(rule.getRuleId())) {
                errors.add("duplicate formula ruleId [" + rule.getRuleId() + "]");
            }
            if (rule.getOutputAttribute() == null || !attributeIds.contains(rule.getOutputAttribute())) {
                errors.add("formula rule [" + rule.getRuleId() + "] outputs attribute ["
                        + rule.getOutputAttribute() + "] which is not declared in attributes.json");
            }
            checkFormulaIdentifiers(rule, errors, attributeIds);
        }

        for (WorkflowStage stage : config.getStages()) {
            if (stage.getRuleIds() == null || stage.getRuleIds().isEmpty()) {
                errors.add("workflow stage [" + stage.getName() + "] lists no ruleIds");
                continue;
            }
            for (String ruleId : stage.getRuleIds()) {
                if (stage.getEngine() == EngineType.ELIGIBILITY && !eligibilityRuleIds.contains(ruleId)) {
                    errors.add("workflow stage [" + stage.getName() + "] references eligibility ruleId ["
                            + ruleId + "] which does not exist in eligibility-rules.json");
                }
                if (stage.getEngine() == EngineType.FORMULA && !formulaRuleIds.contains(ruleId)) {
                    errors.add("workflow stage [" + stage.getName() + "] references formula ruleId ["
                            + ruleId + "] which does not exist in formula-rules.json");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new ConfigValidationException("Payroll config validation failed:\n  - " + String.join("\n  - ", errors));
        }
    }

    private static void checkFormulaIdentifiers(FormulaRuleConfig rule, List<String> errors, Set<String> attributeIds) {
        for (String expression : List.of(rule.getCondition(), rule.getFormula())) {
            if (expression == null) {
                continue;
            }
            String withoutLiterals = STRING_LITERAL.matcher(expression).replaceAll(" ");
            Matcher m = IDENTIFIER.matcher(withoutLiterals);
            while (m.find()) {
                String id = m.group();
                if (!MVEL_LITERALS.contains(id) && !attributeIds.contains(id)) {
                    errors.add("formula rule [" + rule.getRuleId() + "] expression references attribute ["
                            + id + "] which is not declared in attributes.json");
                }
            }
        }
    }
}