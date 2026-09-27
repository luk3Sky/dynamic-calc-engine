package com.payroll.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One entry of attributes.json — the dictionary of every fact the engines may
 * read or write, including its default value.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class AttributeConfig {

    private String id;
    private String displayName;
    private AttributeDataType dataType;
    private AttributeSource source;
    private JsonNode defaultValue;
}