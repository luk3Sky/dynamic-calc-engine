package com.payroll.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One Drools-oriented eligibility/classification rule from
 * eligibility-rules.json. When every condition matches, the derived facts are
 * written into the CalculationContext.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class EligibilityRuleConfig {

    private String ruleId;
    private String description;
    private List<ConditionConfig> conditions;
    private Map<String, JsonNode> derivedFacts;
}