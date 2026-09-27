package com.payroll.common.config;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Structured outcome of {@link ConfigValidator#validate(PayrollConfig)}. Unlike
 * a bare exception, it carries every field-level error so the admin console can
 * render them next to the offending form controls.
 */
@Getter
@AllArgsConstructor
public class ValidationResult {

    private static final ValidationResult VALID = new ValidationResult(List.of());

    private final List<ConfigError> errors;

    public static ValidationResult valid() {
        return VALID;
    }

    public static ValidationResult withErrors(List<ConfigError> errors) {
        return errors.isEmpty() ? VALID : new ValidationResult(List.copyOf(errors));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Renders the errors as a single human-readable message suitable for a
     * fail-fast startup exception or a flash message.
     */
    public String summary() {
        StringBuilder sb = new StringBuilder("Payroll config validation failed:");
        for (ConfigError error : errors) {
            sb.append("\n  - ").append(error.getMessage());
        }
        return sb.toString();
    }

    /** Throws {@link ConfigValidationException} when the config is invalid. */
    public void throwIfInvalid() {
        if (!isValid()) {
            throw new ConfigValidationException(summary());
        }
    }
}