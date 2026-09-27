package com.payroll.common.config;

/**
 * Raised when payroll JSON configuration is missing, malformed, or internally
 * inconsistent. Thrown at startup (fail fast) with an actionable message that
 * names the offending ruleId / attribute / stage.
 */
public class ConfigValidationException extends RuntimeException {

    public ConfigValidationException(String message) {
        super(message);
    }

    public ConfigValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}