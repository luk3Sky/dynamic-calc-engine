package com.payroll.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Comparison operators supported by eligibility-rule conditions.
 */
public enum ConditionOperator {
    @JsonProperty("EQUALS")
    EQUALS,
    @JsonProperty("NOT_EQUALS")
    NOT_EQUALS,
    @JsonProperty("IN")
    IN,
    @JsonProperty("GREATER_THAN")
    GREATER_THAN,
    @JsonProperty("LESS_THAN")
    LESS_THAN,
    @JsonProperty("BETWEEN")
    BETWEEN
}