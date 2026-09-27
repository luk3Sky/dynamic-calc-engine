package com.payroll.rulesengine.drools;

import com.fasterxml.jackson.databind.JsonNode;
import com.payroll.common.config.EligibilityRuleConfig;
import java.util.List;
import java.util.Map;

/**
 * Generates an in-memory DRL source string from {@link EligibilityRuleConfig}s
 * at runtime. There are no static .drl files: every condition/derived-fact pair
 * from eligibility-rules.json is translated into a Drools rule whose conditions
 * delegate operator evaluation to {@link EligibilityConditionEvaluator}.
 */
public class EligibilityRuleCompiler {

    private static final String PACKAGE = "com.payroll.rulesengine.drools.generated";

    /**
     * Compiles the given rules into a single DRL document.
     */
    public String compile(List<EligibilityRuleConfig> rules) {
        StringBuilder drl = new StringBuilder();
        drl.append("package ").append(PACKAGE).append(";\n\n");
        drl.append("import com.payroll.common.domain.CalculationContext;\n");
        drl.append("import com.payroll.rulesengine.drools.EligibilityConditionEvaluator;\n\n");

        for (EligibilityRuleConfig rule : rules) {
            drl.append(buildRule(rule)).append('\n');
        }
        return drl.toString();
    }

    private String buildRule(EligibilityRuleConfig rule) {
        StringBuilder sb = new StringBuilder();
        sb.append("rule \"").append(escape(rule.getRuleId())).append("\"\n");
        sb.append("    no-loop true\n");
        sb.append("when\n");
        sb.append("    $ctx : CalculationContext()\n");
        for (var condition : rule.getConditions()) {
            sb.append("    eval( EligibilityConditionEvaluator.matches($ctx, \"")
                    .append(escape(condition.getAttribute())).append("\", \"")
                    .append(condition.getOperator().name()).append("\", ")
                    .append(javaLiteral(condition.getValue())).append(") )\n");
        }
        sb.append("then\n");
        for (Map.Entry<String, JsonNode> fact : rule.getDerivedFacts().entrySet()) {
            sb.append("    $ctx.setAttribute(\"").append(escape(fact.getKey())).append("\", ")
                    .append(javaLiteral(fact.getValue())).append(");\n");
        }
        sb.append("    $ctx.addTrace(\"").append(escape(rule.getRuleId())).append(": ")
                .append(escape(derivedSummary(rule.getDerivedFacts()))).append("\");\n");
        sb.append("end\n");
        return sb.toString();
    }

    /**
     * Renders a JSON value as a Java expression. Numbers always become
     * {@code new java.math.BigDecimal("...")} so monetary/rate facts stay
     * BigDecimal end-to-end.
     */
    private String javaLiteral(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isTextual()) {
            return "\"" + escape(node.asText()) + "\"";
        }
        if (node.isBoolean()) {
            return String.valueOf(node.asBoolean());
        }
        if (node.isNumber()) {
            return "new java.math.BigDecimal(\"" + node.decimalValue().toPlainString() + "\")";
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder("java.util.Arrays.asList(");
            boolean first = true;
            for (JsonNode element : node) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append(javaLiteral(element));
                first = false;
            }
            return sb.append(')').toString();
        }
        throw new IllegalArgumentException("Unsupported JSON value node: " + node.getNodeType());
    }

    private String derivedSummary(Map<String, JsonNode> derivedFacts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, JsonNode> e : derivedFacts.entrySet()) {
            if (!first) {
                sb.append("; ");
            }
            sb.append(e.getKey()).append(" = ").append(plain(e.getValue()));
            first = false;
        }
        return sb.toString();
    }

    private String plain(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return String.valueOf(node.asBoolean());
        }
        if (node.isNumber()) {
            return node.decimalValue().toPlainString();
        }
        return node.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}