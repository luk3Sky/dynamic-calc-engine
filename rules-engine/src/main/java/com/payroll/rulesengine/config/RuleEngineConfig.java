package com.payroll.rulesengine.config;

import com.payroll.common.config.ConfigLoaderService;
import com.payroll.common.config.ConfigValidator;
import com.payroll.common.config.JacksonConfigLoaderService;
import com.payroll.rulesengine.drools.DroolsEligibilityEngine;
import com.payroll.rulesengine.jeasy.WorkflowExecutor;
import com.payroll.rulesengine.orchestration.PayrollCalculationOrchestrator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the JSON config and both rule engines as Spring beans.
 *
 * <p>{@link ConfigRuntime} is the shared, mutable, thread-safe holder for the
 * currently active config generation (Drools KieBase + jEasy rule sets). It is
 * compiled once here at startup and recompiled in place whenever the admin
 * console calls {@code reload} — the orchestrator and the admin UI read from
 * and write to this single instance, so a live reload changes the next
 * calculation without a restart.
 */
@Configuration
public class RuleEngineConfig {

    @Bean
    public ConfigLoaderService configLoaderService(
            @Value("${payroll.config.location:classpath:sample-config/}") String configLocation) {
        return new JacksonConfigLoaderService(configLocation);
    }

    @Bean
    public ConfigValidator configValidator() {
        return new ConfigValidator();
    }

    @Bean
    public ConfigRuntime configRuntime(ConfigLoaderService configLoader, ConfigValidator configValidator) {
        return new ConfigRuntime(configLoader.loadConfig(), configValidator);
    }

    @Bean
    public DroolsEligibilityEngine droolsEligibilityEngine(ConfigRuntime configRuntime) {
        return new DroolsEligibilityEngine(configRuntime);
    }

    @Bean
    public WorkflowExecutor workflowExecutor(ConfigRuntime configRuntime) {
        return new WorkflowExecutor(configRuntime);
    }

    @Bean
    public PayrollCalculationOrchestrator payrollCalculationOrchestrator(
            ConfigRuntime configRuntime,
            DroolsEligibilityEngine droolsEligibilityEngine,
            WorkflowExecutor workflowExecutor) {
        return new PayrollCalculationOrchestrator(configRuntime, droolsEligibilityEngine, workflowExecutor);
    }
}