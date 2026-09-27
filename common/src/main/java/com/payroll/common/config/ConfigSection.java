package com.payroll.common.config;

/**
 * Identifies one of the four payroll configuration files. Used to scope
 * validation errors and to resolve the on-disk path of each file when the
 * admin console persists a config.
 */
public enum ConfigSection {
    ATTRIBUTES("attributes.json"),
    ELIGIBILITY_RULES("eligibility-rules.json"),
    FORMULA_RULES("formula-rules.json"),
    WORKFLOW("workflow.json");

    private final String fileName;

    ConfigSection(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }
}