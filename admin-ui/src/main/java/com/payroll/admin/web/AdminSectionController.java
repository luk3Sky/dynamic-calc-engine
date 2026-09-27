package com.payroll.admin.web;

import com.payroll.common.config.AttributeDataType;
import com.payroll.common.config.AttributeSource;
import com.payroll.common.config.ConditionOperator;
import com.payroll.common.config.ConfigError;
import com.payroll.common.config.PayrollConfig;
import com.payroll.common.config.StageExecution;
import com.payroll.common.domain.ComponentType;
import java.util.List;
import java.util.Map;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Shared base for the four admin editors: resolves the uniform
 * Validate / Apply to Runtime / Save to File actions against a pending candidate
 * config, and populates the common model attributes every page needs
 * (nav highlight, current in-memory config, dropdown sources, error grouping).
 */
public abstract class AdminSectionController {

    protected final AdminConfigService service;

    protected AdminSectionController(AdminConfigService service) {
        this.service = service;
    }

    /**
     * Handles a form's {@code action} parameter against the candidate config.
     * Returns the redirect view to send the browser to on success, or
     * {@code null} when validation failed (caller must re-render with errors).
     */
    protected String resolve(String page, PayrollConfig candidate, String action,
            Model model, RedirectAttributes redirect) {
        var result = service.validate(candidate);
        if (!result.isValid()) {
            model.addAttribute("validationErrors", result.getErrors());
            model.addAttribute("errorsByKey", AdminForms.groupErrorsByKey(result.getErrors()));
            return null;
        }
        switch (action) {
            case "validate" -> redirect.addFlashAttribute("flash",
                    "Configuration is valid — nothing was changed.");
            case "apply" -> {
                service.applyToRuntime(candidate);
                redirect.addFlashAttribute("flash",
                        "Applied to runtime. The next calculation uses the new rules; nothing was written to disk.");
            }
            case "save" -> {
                service.saveToFile(candidate);
                redirect.addFlashAttribute("flash",
                        "Saved to file (with a .bak backup of the previous version).");
            }
            default -> throw new IllegalStateException("Unknown admin action: " + action);
        }
        return "redirect:" + page;
    }

    /** Populates the model attributes shared by every admin page. */
    protected void populate(PayrollConfig config, String activeNav, Model model) {
        model.addAttribute("activeNav", activeNav);
        model.addAttribute("config", config);
        model.addAttribute("attributes", config.getAttributes());
        model.addAttribute("eligibilityRules", config.getEligibilityRules());
        model.addAttribute("formulaRules", config.getFormulaRules());
        model.addAttribute("stages", config.getStages());
        model.addAttribute("dataTypes", AttributeDataType.values());
        model.addAttribute("sources", AttributeSource.values());
        model.addAttribute("operators", ConditionOperator.values());
        model.addAttribute("componentTypes", ComponentType.values());
        model.addAttribute("stageExecutions", StageExecution.values());
        model.addAttribute("writable", service.isWritable());
        Map<String, String> dataTypesByAttribute = new java.util.LinkedHashMap<>();
        for (com.payroll.common.config.AttributeConfig attribute : config.getAttributes()) {
            dataTypesByAttribute.put(attribute.getId(),
                    attribute.getDataType() == null ? null : attribute.getDataType().name());
        }
        model.addAttribute("dataTypesByAttribute", dataTypesByAttribute);
        @SuppressWarnings("unchecked")
        List<ConfigError> validationErrors = (List<ConfigError>) model.getAttribute("validationErrors");
        model.addAttribute("errorsByKey", validationErrors == null || validationErrors.isEmpty()
                ? Map.of()
                : AdminForms.groupErrorsByKey(validationErrors));
    }

    /** Human-friendly string of the current config location for page footers. */
    protected String formatInstant(java.time.Instant instant) {
        return instant == null ? "—" : java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(instant);
    }
}