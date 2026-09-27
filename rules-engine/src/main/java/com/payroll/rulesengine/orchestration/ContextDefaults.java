package com.payroll.rulesengine.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.payroll.common.config.AttributeConfig;
import com.payroll.common.config.AttributeDataType;
import com.payroll.common.config.PayrollConfig;
import com.payroll.common.domain.CalculationContext;
import java.math.BigDecimal;

/**
 * Pre-seeds every attribute declared in attributes.json that the request did
 * not supply with its configured defaultValue. This is what makes formulas safe
 * to run against the live attribute map: every referenced attribute always
 * exists (MVEL throws on unresolved identifiers), and inapplicable components
 * simply remain at their zero default until a formula overrides them.
 */
public final class ContextDefaults {

    private ContextDefaults() {
    }

    public static CalculationContext apply(CalculationContext context, PayrollConfig config) {
        for (AttributeConfig attribute : config.getAttributes()) {
            JsonNode defaultValue = attribute.getDefaultValue();
            if (defaultValue == null || defaultValue.isNull()) {
                continue;
            }
            if (context.hasAttribute(attribute.getId())) {
                continue;
            }
            context.setAttribute(attribute.getId(), toValue(defaultValue, attribute.getDataType()));
        }
        return context;
    }

    private static Object toValue(JsonNode node, AttributeDataType dataType) {
        return switch (dataType) {
            case NUMBER -> node.decimalValue() instanceof BigDecimal decimal ? decimal : new BigDecimal(node.asText());
            case STRING -> node.asText();
            case BOOLEAN -> node.asBoolean();
        };
    }
}