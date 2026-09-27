package com.payroll.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Origin of an attribute value in attributes.json.
 */
public enum AttributeSource {
    @JsonProperty("EMPLOYEE_MASTER")
    EMPLOYEE_MASTER,
    @JsonProperty("ATTENDANCE")
    ATTENDANCE,
    @JsonProperty("ACTIVITY")
    ACTIVITY,
    @JsonProperty("COMPUTED")
    COMPUTED
}