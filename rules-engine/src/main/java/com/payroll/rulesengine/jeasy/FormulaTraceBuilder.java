package com.payroll.rulesengine.jeasy;

import com.payroll.common.config.FormulaRuleConfig;
import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the audit line appended to the calculation trace after a formula
 * evaluates, e.g.
 * {@code OVERTIME_PAY: hourlyRate(25.00) * overtimeHours(4) * overtimeMultiplier(1.5) = 150.00}.
 * Identifiers that exist as attributes are annotated with their actual value so
 * the trace is self-explanatory without any hardcoded formatting per rule.
 */
public final class FormulaTraceBuilder {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private FormulaTraceBuilder() {
    }

    public static String build(FormulaRuleConfig rule, Map<String, Object> attributes, BigDecimal result) {
        return rule.getRuleId() + ": " + substitute(rule.getFormula(), attributes) + " = " + result.toPlainString();
    }

    private static String substitute(String formula, Map<String, Object> attributes) {
        Matcher matcher = IDENTIFIER.matcher(formula);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String id = matcher.group();
            String replacement = attributes.containsKey(id)
                    ? id + "(" + format(attributes.get(id)) + ")"
                    : id;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String format(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return String.valueOf(value);
    }
}