package com.payroll.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ordering semantics of a workflow stage. easy-rules 4.x removed rule groups
 * (UnitRuleGroup / ActivationRuleGroup); SEQUENTIAL stages are enforced by rule
 * priority inside a stage's Rules set, while PARALLEL stages only guarantee
 * rule independence.
 */
public enum StageExecution {
    @JsonProperty("SEQUENTIAL")
    SEQUENTIAL,
    @JsonProperty("PARALLEL")
    PARALLEL
}