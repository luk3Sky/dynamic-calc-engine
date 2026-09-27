package com.payroll.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Which rule engine executes a workflow stage. Keeps Drools and jEasy fully
 * decoupled: only the orchestrator in rules-engine dispatches on this.
 */
public enum EngineType {
    @JsonProperty("ELIGIBILITY")
    ELIGIBILITY,
    @JsonProperty("FORMULA")
    FORMULA
}