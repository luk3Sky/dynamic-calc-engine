package com.payroll.common.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data types allowed for attributes in attributes.json.
 */
public enum AttributeDataType {
    @JsonProperty("NUMBER")
    NUMBER,
    @JsonProperty("STRING")
    STRING,
    @JsonProperty("BOOLEAN")
    BOOLEAN
}