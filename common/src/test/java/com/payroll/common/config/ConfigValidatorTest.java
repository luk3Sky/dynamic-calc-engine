package com.payroll.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConfigValidator}, covering each cross-reference /
 * structural rule and asserting on the returned {@link ValidationResult}
 * contents (section, context, field, message) rather than on an exception.
 */
class ConfigValidatorTest {

    private final ConfigValidator validator = new ConfigValidator();
    private final JsonNodeFactory nodes = JsonNodeFactory.instance;

    @Test
    void validConfigPassesWithoutErrors() {
        PayrollConfig config = PayrollConfig.builder()
                .attributes(List.of(
                        attribute("grossTotal", AttributeDataType.NUMBER),
                        attribute("taxSlab", AttributeDataType.STRING)))
                .eligibilityRules(List.of(eligibilityRule("RULE_1", List.of(
                        condition("grossTotal", ConditionOperator.GREATER_THAN, nodes.numberNode(0))),
                        Map.of("taxSlab", nodes.textNode("SLAB_1")))))
                .formulaRules(List.of(formulaRule("RULE_F", "grossTotal")))
                .stages(List.of(stage("S_ELIG", EngineType.ELIGIBILITY, List.of("RULE_1")),
                        stage("S_FORMULA", EngineType.FORMULA, List.of("RULE_F"))))
                .build();

        ValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void missingAttributeReferenceInEligibilityConditionIsReportedWithFieldContext() {
        PayrollConfig config = PayrollConfig.builder()
                .attributes(List.of())
                .eligibilityRules(List.of(eligibilityRule("RULE_GHOST", List.of(
                        condition("ghostAttribute", ConditionOperator.EQUALS, nodes.numberNode(1))),
                        Map.of("x", nodes.booleanNode(true)))))
                .formulaRules(List.of())
                .stages(List.of())
                .build();

        ValidationResult result = validator.validate(config);

        assertThat(result.isValid()).isFalse();
        ConfigError error = singleMatching(result, "RULE_GHOST", "conditions[0].attribute");
        assertThat(error.getSection()).isEqualTo(ConfigSection.ELIGIBILITY_RULES);
        assertThat(error.getMessage()).contains("ghostAttribute").contains("not declared");
    }

    @Test
    void missingRuleIdInEligibilityRuleIsReported() {
        PayrollConfig config = PayrollConfig.builder()
                .attributes(List.of(attribute("a", AttributeDataType.NUMBER)))
                .eligibilityRules(List.of(eligibilityRule("  ", List.of(
                        condition("a", ConditionOperator.EQUALS, nodes.numberNode(1))),
                        Map.of("b", nodes.booleanNode(true)))))
                .formulaRules(List.of())
                .stages(List.of())
                .build();

        ValidationResult result = validator.validate(config);

        ConfigError error = singleMatching(result, "", "ruleId");
        assertThat(error.getSection()).isEqualTo(ConfigSection.ELIGIBILITY_RULES);
        assertThat(error.getMessage()).contains("ruleId is required");
    }

    @Test
    void duplicateAttributeIdIsReportedAgainstTheDuplicateRow() {
        PayrollConfig config = PayrollConfig.builder()
                .attributes(List.of(
                        attribute("hourlyRate", AttributeDataType.NUMBER),
                        attribute("hourlyRate", AttributeDataType.NUMBER)))
                .eligibilityRules(List.of())
                .formulaRules(List.of())
                .stages(List.of())
                .build();

        ValidationResult result = validator.validate(config);

        ConfigError error = singleMatching(result, "hourlyRate", "id");
        assertThat(error.getSection()).isEqualTo(ConfigSection.ATTRIBUTES);
        assertThat(error.getMessage()).contains("duplicate attribute id [hourlyRate]");
    }

    @Test
    void emptyWorkflowStageIsReported() {
        PayrollConfig config = PayrollConfig.builder()
                .attributes(List.of())
                .eligibilityRules(List.of())
                .formulaRules(List.of())
                .stages(List.of(stage("EMPTY_STAGE", EngineType.FORMULA, List.of())))
                .build();

        ValidationResult result = validator.validate(config);

        ConfigError error = singleMatching(result, "EMPTY_STAGE", "ruleIds");
        assertThat(error.getSection()).isEqualTo(ConfigSection.WORKFLOW);
        assertThat(error.getMessage()).contains("no ruleIds");
    }

    @Test
    void unknownFormulaRuleIdInFormulaStageIsReported() {
        PayrollConfig config = PayrollConfig.builder()
                .attributes(List.of(attribute("grossTotal", AttributeDataType.NUMBER)))
                .eligibilityRules(List.of())
                .formulaRules(List.of(formulaRule("RULE_F", "grossTotal")))
                .stages(List.of(stage("S", EngineType.FORMULA, List.of("RULE_DOES_NOT_EXIST"))))
                .build();

        ValidationResult result = validator.validate(config);

        ConfigError error = singleMatching(result, "S", "ruleIds");
        assertThat(error.getMessage()).contains("RULE_DOES_NOT_EXIST").contains("does not exist");
    }

    @Test
    void defaultValueMismatchingDataTypeIsReported() {
        PayrollConfig config = PayrollConfig.builder()
                .attributes(List.of(AttributeConfig.builder()
                        .id("rate")
                        .dataType(AttributeDataType.NUMBER)
                        .source(AttributeSource.EMPLOYEE_MASTER)
                        .defaultValue(nodes.textNode("not-a-number"))
                        .build()))
                .eligibilityRules(List.of())
                .formulaRules(List.of())
                .stages(List.of())
                .build();

        ValidationResult result = validator.validate(config);

        ConfigError error = singleMatching(result, "rate", "defaultValue");
        assertThat(error.getSection()).isEqualTo(ConfigSection.ATTRIBUTES);
        assertThat(error.getMessage()).contains("must be a number");
    }

    @Test
    void betweenOperatorRequiresTwoElementArray() {
        PayrollConfig config = PayrollConfig.builder()
                .attributes(List.of(attribute("grossTotal", AttributeDataType.NUMBER)))
                .eligibilityRules(List.of(eligibilityRule("RULE_B", List.of(
                        condition("grossTotal", ConditionOperator.BETWEEN, nodes.numberNode(5))),
                        Map.of("taxSlab", nodes.textNode("S")))))
                .formulaRules(List.of())
                .stages(List.of())
                .build();

        ValidationResult result = validator.validate(config);

        ConfigError error = singleMatching(result, "RULE_B", "conditions[0].value");
        assertThat(error.getMessage()).contains("BETWEEN").contains("two-element array");
    }

    private ConfigError singleMatching(ValidationResult result, String context, String field) {
        List<ConfigError> matching = result.getErrors().stream()
                .filter(e -> e.getContext().equals(context) && e.getField().equals(field))
                .toList();
        assertThat(matching).as("exactly one error for context=%s field=%s but got %s",
                context, field, result.getErrors()).hasSize(1);
        return matching.get(0);
    }

    private AttributeConfig attribute(String id, AttributeDataType dataType) {
        return AttributeConfig.builder().id(id).dataType(dataType).source(AttributeSource.COMPUTED).build();
    }

    private EligibilityRuleConfig eligibilityRule(String ruleId, List<ConditionConfig> conditions,
            Map<String, JsonNode> derivedFacts) {
        return EligibilityRuleConfig.builder()
                .ruleId(ruleId)
                .conditions(conditions)
                .derivedFacts(derivedFacts)
                .build();
    }

    private ConditionConfig condition(String attribute, ConditionOperator operator, JsonNode value) {
        return ConditionConfig.builder().attribute(attribute).operator(operator).value(value).build();
    }

    private FormulaRuleConfig formulaRule(String ruleId, String outputAttribute) {
        return FormulaRuleConfig.builder()
                .ruleId(ruleId)
                .name(ruleId)
                .priority(1)
                .condition("true")
                .formula("grossTotal")
                .outputAttribute(outputAttribute)
                .build();
    }

    private WorkflowStage stage(String name, EngineType engine, List<String> ruleIds) {
        return WorkflowStage.builder()
                .name(name)
                .engine(engine)
                .ruleIds(ruleIds)
                .execution(StageExecution.SEQUENTIAL)
                .build();
    }
}