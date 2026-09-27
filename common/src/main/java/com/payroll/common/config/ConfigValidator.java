package com.payroll.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cross-file structural validation of the payroll JSON configuration. The single
 * implementation of validation for both startup config loading and the admin
 * console: any edit is validated here before it may be applied to runtime or
 * saved to disk, so the two paths can never disagree.
 *
 * <p>Returns a structured {@link ValidationResult} whose errors carry the
 * offending section / ruleId / attribute / stage and the affected field path,
 * so the admin UI can render them next to the offending form control.
 */
public class ConfigValidator {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern STRING_LITERAL = Pattern.compile("'[^']*'|\"[^\"]*\"");
    private static final Set<String> MVEL_LITERALS = Set.of(
            "true", "false", "null", "new", "if", "else", "for", "while", "do",
            "return", "def", "function", "import", "as", "in", "is", "not",
            "and", "or", "instanceof", "switch", "case", "default", "try",
            "catch", "finally", "throw", "this", "break", "continue");

    /**
     * Validates the config and returns every detected inconsistency. A valid
     * config yields {@link ValidationResult#valid()}.
     */
    public ValidationResult validate(PayrollConfig config) {
        List<ConfigError> errors = new ArrayList<>();
        validateAttributes(config, errors);
        validateEligibilityRules(config, errors);
        validateFormulaRules(config, errors);
        validateWorkflow(config, errors);
        return ValidationResult.withErrors(errors);
    }

    private void validateAttributes(PayrollConfig config, List<ConfigError> errors) {
        Set<String> attributeIds = new HashSet<>();
        for (AttributeConfig attr : config.getAttributes()) {
            String context = attr.getId() == null ? "" : attr.getId();
            if (attr.getId() == null || attr.getId().isBlank()) {
                errors.add(error(ConfigSection.ATTRIBUTES, context, "id", "attribute id is required"));
                continue;
            }
            if (!attributeIds.add(attr.getId())) {
                errors.add(error(ConfigSection.ATTRIBUTES, attr.getId(), "id",
                        "duplicate attribute id [" + attr.getId() + "]"));
            }
            if (attr.getDataType() == null) {
                errors.add(error(ConfigSection.ATTRIBUTES, attr.getId(), "dataType",
                        "dataType is required"));
            }
            JsonNode defaultValue = attr.getDefaultValue();
            if (defaultValue != null && !defaultValue.isNull() && attr.getDataType() != null
                    && !matchesDataType(defaultValue, attr.getDataType())) {
                errors.add(error(ConfigSection.ATTRIBUTES, attr.getId(), "defaultValue",
                        "defaultValue must be a " + attr.getDataType().name().toLowerCase() + " value, was ["
                                + defaultValue + "]"));
            }
        }
    }

    private void validateEligibilityRules(PayrollConfig config, List<ConfigError> errors) {
        Set<String> attributeIds = new HashSet<>();
        for (AttributeConfig attr : config.getAttributes()) {
            if (attr.getId() != null) {
                attributeIds.add(attr.getId());
            }
        }

        Set<String> ruleIds = new HashSet<>();
        for (EligibilityRuleConfig rule : config.getEligibilityRules()) {
            String ruleId = rule.getRuleId();
            if (ruleId == null || ruleId.isBlank()) {
                errors.add(error(ConfigSection.ELIGIBILITY_RULES, "", "ruleId", "ruleId is required"));
                continue;
            }
            if (!ruleIds.add(ruleId)) {
                errors.add(error(ConfigSection.ELIGIBILITY_RULES, ruleId, "ruleId",
                        "duplicate ruleId [" + ruleId + "]"));
            }
            List<ConditionConfig> conditions = rule.getConditions();
            if (conditions == null || conditions.isEmpty()) {
                errors.add(error(ConfigSection.ELIGIBILITY_RULES, ruleId, "conditions",
                        "rule has no conditions"));
            } else {
                for (int i = 0; i < conditions.size(); i++) {
                    ConditionConfig condition = conditions.get(i);
                    String field = "conditions[" + i + "]";
                    if (condition.getAttribute() == null || !attributeIds.contains(condition.getAttribute())) {
                        errors.add(error(ConfigSection.ELIGIBILITY_RULES, ruleId, field + ".attribute",
                                "condition references attribute [" + condition.getAttribute()
                                        + "] which is not declared in attributes.json"));
                    }
                    JsonNode value = condition.getValue();
                    if (condition.getOperator() == ConditionOperator.BETWEEN
                            && (value == null || !value.isArray() || value.size() != 2)) {
                        errors.add(error(ConfigSection.ELIGIBILITY_RULES, ruleId, field + ".value",
                                "BETWEEN requires a two-element array value"));
                    }
                    if (condition.getOperator() == ConditionOperator.IN
                            && (value == null || !value.isArray())) {
                        errors.add(error(ConfigSection.ELIGIBILITY_RULES, ruleId, field + ".value",
                                "IN requires an array value"));
                    }
                }
            }
            var derivedFacts = rule.getDerivedFacts();
            if (derivedFacts == null || derivedFacts.isEmpty()) {
                errors.add(error(ConfigSection.ELIGIBILITY_RULES, ruleId, "derivedFacts",
                        "rule has no derived facts"));
            } else {
                for (String key : derivedFacts.keySet()) {
                    if (!attributeIds.contains(key)) {
                        errors.add(error(ConfigSection.ELIGIBILITY_RULES, ruleId, "derivedFacts." + key,
                                "derives attribute [" + key + "] which is not declared in attributes.json"));
                    }
                }
            }
        }
    }

    private void validateFormulaRules(PayrollConfig config, List<ConfigError> errors) {
        Set<String> attributeIds = new HashSet<>();
        for (AttributeConfig attr : config.getAttributes()) {
            if (attr.getId() != null) {
                attributeIds.add(attr.getId());
            }
        }

        Set<String> ruleIds = new HashSet<>();
        for (FormulaRuleConfig rule : config.getFormulaRules()) {
            String ruleId = rule.getRuleId();
            if (ruleId == null || ruleId.isBlank()) {
                errors.add(error(ConfigSection.FORMULA_RULES, "", "ruleId", "ruleId is required"));
                continue;
            }
            if (!ruleIds.add(ruleId)) {
                errors.add(error(ConfigSection.FORMULA_RULES, ruleId, "ruleId",
                        "duplicate ruleId [" + ruleId + "]"));
            }
            if (rule.getOutputAttribute() == null || !attributeIds.contains(rule.getOutputAttribute())) {
                errors.add(error(ConfigSection.FORMULA_RULES, ruleId, "outputAttribute",
                        "outputs attribute [" + rule.getOutputAttribute()
                                + "] which is not declared in attributes.json"));
            }
            checkFormulaIdentifiers(rule, errors, attributeIds);
        }
    }

    private void validateWorkflow(PayrollConfig config, List<ConfigError> errors) {
        Set<String> eligibilityRuleIds = new HashSet<>();
        for (EligibilityRuleConfig rule : config.getEligibilityRules()) {
            if (rule.getRuleId() != null) {
                eligibilityRuleIds.add(rule.getRuleId());
            }
        }
        Set<String> formulaRuleIds = new HashSet<>();
        for (FormulaRuleConfig rule : config.getFormulaRules()) {
            if (rule.getRuleId() != null) {
                formulaRuleIds.add(rule.getRuleId());
            }
        }

        for (WorkflowStage stage : config.getStages()) {
            String stageName = stage.getName() == null || stage.getName().isBlank() ? "(unnamed)" : stage.getName();
            if (stage.getName() == null || stage.getName().isBlank()) {
                errors.add(error(ConfigSection.WORKFLOW, stageName, "name", "stage name is required"));
            }
            if (stage.getRuleIds() == null || stage.getRuleIds().isEmpty()) {
                errors.add(error(ConfigSection.WORKFLOW, stageName, "ruleIds", "stage lists no ruleIds"));
                continue;
            }
            for (String ruleId : stage.getRuleIds()) {
                if (stage.getEngine() == EngineType.ELIGIBILITY && !eligibilityRuleIds.contains(ruleId)) {
                    errors.add(error(ConfigSection.WORKFLOW, stageName, "ruleIds",
                            "references eligibility ruleId [" + ruleId
                                    + "] which does not exist in eligibility-rules.json"));
                }
                if (stage.getEngine() == EngineType.FORMULA && !formulaRuleIds.contains(ruleId)) {
                    errors.add(error(ConfigSection.WORKFLOW, stageName, "ruleIds",
                            "references formula ruleId [" + ruleId
                                    + "] which does not exist in formula-rules.json"));
                }
            }
        }
    }

    private void checkFormulaIdentifiers(FormulaRuleConfig rule, List<ConfigError> errors, Set<String> attributeIds) {
        checkExpression(rule, rule.getCondition(), "condition", errors, attributeIds);
        checkExpression(rule, rule.getFormula(), "formula", errors, attributeIds);
    }

    private void checkExpression(FormulaRuleConfig rule, String expression, String field,
            List<ConfigError> errors, Set<String> attributeIds) {
        if (expression == null) {
            return;
        }
        String withoutLiterals = STRING_LITERAL.matcher(expression).replaceAll(" ");
        Matcher m = IDENTIFIER.matcher(withoutLiterals);
        while (m.find()) {
            String id = m.group();
            if (!MVEL_LITERALS.contains(id) && !attributeIds.contains(id)) {
                errors.add(error(ConfigSection.FORMULA_RULES, rule.getRuleId(), field,
                        "expression references attribute [" + id + "] which is not declared in attributes.json"));
            }
        }
    }

    private boolean matchesDataType(JsonNode node, AttributeDataType dataType) {
        return switch (dataType) {
            case NUMBER -> node.isNumber();
            case STRING -> node.isTextual();
            case BOOLEAN -> node.isBoolean();
        };
    }

    private ConfigError error(ConfigSection section, String context, String field, String message) {
        return ConfigError.builder()
                .section(section)
                .context(context)
                .field(field)
                .message(message)
                .build();
    }
}