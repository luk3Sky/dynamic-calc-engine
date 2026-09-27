package com.payroll.admin.web;

import com.payroll.admin.persistence.ConfigFileStore;
import com.payroll.common.config.AttributeConfig;
import com.payroll.common.config.ConfigLoaderService;
import com.payroll.common.config.ConfigValidator;
import com.payroll.common.config.EligibilityRuleConfig;
import com.payroll.common.config.FormulaRuleConfig;
import com.payroll.common.config.PayrollConfig;
import com.payroll.common.config.ValidationResult;
import com.payroll.common.config.WorkflowStage;
import com.payroll.rulesengine.config.ConfigRuntime;
import org.springframework.stereotype.Component;

/**
 * The single implementation of the admin console's three persistence semantics,
 * shared verbatim by all four editors:
 *
 * <ul>
 *   <li><b>Validate</b> — runs {@link ConfigValidator} against the pending edit
 *       and returns the {@link ValidationResult}; nothing else changes.</li>
 *   <li><b>Apply to Runtime</b> — validates, then calls
 *       {@link ConfigRuntime#reload}, recompiling the Drools KieBase and the
 *       jEasy rule sets so the next calculation uses the edit. Nothing touches
 *       disk. An invalid edit hard-blocks (reload itself rejects it).</li>
 *   <li><b>Save to File</b> — validates, applies to runtime, then writes the
 *       config to the original JSON files (with a rolling {@code .bak}) and
 *       refreshes the disk snapshot so the dashboard dirty flag is accurate.
 *       Because disk becomes the source of truth, Save also applies the edit to
 *       the running engine so memory and disk can never disagree.</li>
 *   <li><b>Reload from Disk</b> — re-reads the JSON files through
 *       {@link ConfigLoaderService} and activates them, discarding any unsaved
 *       in-memory edits.</li>
 * </ul>
 */
@Component
public class AdminConfigService {

    private final ConfigValidator validator;
    private final ConfigRuntime runtime;
    private final ConfigFileStore fileStore;
    private final ConfigLoaderService configLoader;

    public AdminConfigService(ConfigValidator validator, ConfigRuntime runtime,
            ConfigFileStore fileStore, ConfigLoaderService configLoader) {
        this.validator = validator;
        this.runtime = runtime;
        this.fileStore = fileStore;
        this.configLoader = configLoader;
    }

    public ValidationResult validate(PayrollConfig candidate) {
        return validator.validate(candidate);
    }

    /** Current in-memory (live) config that the calculation engine reads from. */
    public PayrollConfig currentConfig() {
        return runtime.getConfig();
    }

    /** Candidate config with only the attributes replaced by the pending edit. */
    public PayrollConfig withAttributes(java.util.List<AttributeConfig> attributes) {
        PayrollConfig current = currentConfig();
        return PayrollConfig.builder()
                .attributes(attributes)
                .eligibilityRules(current.getEligibilityRules())
                .formulaRules(current.getFormulaRules())
                .stages(current.getStages())
                .build();
    }

    /** Candidate config with only the eligibility rules replaced. */
    public PayrollConfig withEligibilityRules(java.util.List<EligibilityRuleConfig> eligibilityRules) {
        PayrollConfig current = currentConfig();
        return PayrollConfig.builder()
                .attributes(current.getAttributes())
                .eligibilityRules(eligibilityRules)
                .formulaRules(current.getFormulaRules())
                .stages(current.getStages())
                .build();
    }

    /** Candidate config with only the formula rules replaced. */
    public PayrollConfig withFormulaRules(java.util.List<FormulaRuleConfig> formulaRules) {
        PayrollConfig current = currentConfig();
        return PayrollConfig.builder()
                .attributes(current.getAttributes())
                .eligibilityRules(current.getEligibilityRules())
                .formulaRules(formulaRules)
                .stages(current.getStages())
                .build();
    }

    /** Candidate config with only the workflow stages replaced. */
    public PayrollConfig withStages(java.util.List<WorkflowStage> stages) {
        PayrollConfig current = currentConfig();
        return PayrollConfig.builder()
                .attributes(current.getAttributes())
                .eligibilityRules(current.getEligibilityRules())
                .formulaRules(current.getFormulaRules())
                .stages(stages)
                .build();
    }

    public void applyToRuntime(PayrollConfig candidate) {
        runtime.reload(candidate);
    }

    public void saveToFile(PayrollConfig candidate) {
        runtime.reload(candidate);
        fileStore.save(runtime.getConfig());
        configLoader.reload();
    }

    public PayrollConfig reloadFromDisk() {
        PayrollConfig fresh = configLoader.reload();
        runtime.reload(fresh);
        return fresh;
    }

    public boolean isWritable() {
        return fileStore.isWritable();
    }
}