package com.payroll.common.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * A single field-level configuration error produced by {@link ConfigValidator}.
 * Carries enough context ({@link ConfigSection}, the offending ruleId /
 * attribute id / stage name and the affected field path) for the admin console
 * to render the message next to the exact form field that caused it.
 */
@Getter
@Builder
@AllArgsConstructor
public class ConfigError {

    private final ConfigSection section;
    private final String context;
    private final String field;
    private final String message;

    /**
     * Stable key used by the admin UI to group errors to a form field:
     * {@code SECTION::context::field}.
     */
    public String getKey() {
        return section.name() + "::" + (context == null ? "" : context) + "::" + (field == null ? "" : field);
    }
}