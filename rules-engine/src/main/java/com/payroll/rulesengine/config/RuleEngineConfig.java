package com.payroll.rulesengine.config;

import com.payroll.common.config.ConfigLoaderService;
import com.payroll.common.config.JacksonConfigLoaderService;
import com.payroll.rulesengine.drools.DroolsEligibilityEngine;
import com.payroll.rulesengine.jeasy.WorkflowExecutor;
import com.payroll.rulesengine.orchestration.PayrollCalculationOrchestrator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the JSON config and both rule engines as Spring beans. The Drools
 * KieBase is compiled once here (at startup) and cached for the life of the
 * application; only a KieSession is created per request.
 */
@Configuration
public class RuleEngineConfig {

    @Bean
    public ConfigLoaderService configLoaderService(
            @Value("${payroll.config.location:classpath:sample-config/}") String configLocation) {
        return new JacksonConfigLoaderService(configLocation);
    }

    @Bean
    public DroolsEligibilityEngine droolsEligibilityEngine(ConfigLoaderService configLoader) {
        return new DroolsEligibilityEngine(configLoader.getEligibilityRules());
    }

    @Bean
    public WorkflowExecutor workflowExecutor(ConfigLoaderService configLoader) {
        return new WorkflowExecutor(configLoader);
    }

    @Bean
    public PayrollCalculationOrchestrator payrollCalculationOrchestrator(
            ConfigLoaderService configLoader,
            DroolsEligibilityEngine droolsEligibilityEngine,
            WorkflowExecutor workflowExecutor) {
        return new PayrollCalculationOrchestrator(configLoader, droolsEligibilityEngine, workflowExecutor);
    }
}