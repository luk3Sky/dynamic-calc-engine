package com.payroll.admin.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.payroll.common.config.AttributeDataType;
import com.payroll.common.config.ConfigError;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converters between the text values a form submits and the JSON values the
 * config model stores, plus grouping of {@link ConfigError}s by their
 * {@code section::context::field} key so templates can render field-level
 * messages next to the offending control.
 */
public final class AdminForms {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private AdminForms() {
    }

    /** Groups errors by their stable {@code section::context::field} key. */
    public static Map<String, List<ConfigError>> groupErrorsByKey(List<ConfigError> errors) {
        Map<String, List<ConfigError>> grouped = new LinkedHashMap<>();
        for (ConfigError error : errors) {
            grouped.computeIfAbsent(error.getKey(), key -> new ArrayList<>()).add(error);
        }
        return grouped;
    }

    /** Parses a single form value into a JsonNode typed by the attribute's dataType. */
    public static JsonNode toNode(String raw, AttributeDataType dataType) {
        if (raw == null || raw.isBlank()) {
            return NODES.nullNode();
        }
        if (dataType == null) {
            // attribute not declared (or not yet chosen): keep the raw text so
            // ConfigValidator can report the unresolved reference with context
            return NODES.textNode(raw);
        }
        return switch (dataType) {
            case NUMBER -> NODES.numberNode(new BigDecimal(raw.trim()));
            case STRING -> NODES.textNode(raw);
            case BOOLEAN -> NODES.booleanNode(Boolean.parseBoolean(raw.trim()));
        };
    }

    /** Parses each entry of a list of form values into a JsonNode array. */
    public static ArrayNode toArrayNode(List<String> values, AttributeDataType dataType) {
        ArrayNode array = NODES.arrayNode();
        if (values == null) {
            return array;
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            array.add(toNode(value, dataType));
        }
        return array;
    }

    /** Renders a JsonNode back to the plain text a form input expects. */
    public static String fromNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.decimalValue().toPlainString();
        }
        return node.asText();
    }

    /**
     * Null-safe dataType lookup for template rendering: returns the attribute's
     * dataType name (or null when the attribute is unknown / not yet chosen).
     */
    public static String dataTypeOf(Map<String, String> dataTypesByAttribute, String attributeId) {
        return attributeId == null ? null : dataTypesByAttribute.get(attributeId);
    }
}