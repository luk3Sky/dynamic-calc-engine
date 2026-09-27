package com.payroll.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single {@code attribute / operator / value} triple inside an
 * eligibility-rule condition.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class ConditionConfig {

    private String attribute;
    private ConditionOperator operator;
    private JsonNode value;
}