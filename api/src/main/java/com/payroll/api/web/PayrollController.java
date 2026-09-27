package com.payroll.api.web;

import com.payroll.api.web.dto.ConfigRulesView;
import com.payroll.api.web.dto.PayrollCalculationRequest;
import com.payroll.common.config.AttributeConfig;
import com.payroll.common.config.ConfigLoaderService;
import com.payroll.common.domain.CalculationContext;
import com.payroll.common.domain.PayrollResult;
import com.payroll.rulesengine.orchestration.PayrollCalculationOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read/write payroll API. Calculation is stateless: every request is computed
 * from the request body against the JSON configuration loaded at startup.
 */
@RestController
@RequestMapping("/api/v1/payroll")
@Tag(name = "Payroll", description = "Payroll calculation and configuration introspection")
public class PayrollController {

    private final PayrollCalculationOrchestrator orchestrator;
    private final PayrollCalculationAssembler assembler;
    private final ConfigLoaderService configLoader;

    public PayrollController(
            PayrollCalculationOrchestrator orchestrator,
            PayrollCalculationAssembler assembler,
            ConfigLoaderService configLoader) {
        this.orchestrator = orchestrator;
        this.assembler = assembler;
        this.configLoader = configLoader;
    }

    @PostMapping("/calculate")
    @Operation(summary = "Calculate payroll for an employee",
            description = "Runs the Drools eligibility + jEasy formula pipeline over the request and returns a fully itemized PayrollResult with audit trace.")
    public PayrollResult calculate(@Valid @RequestBody PayrollCalculationRequest request) {
        CalculationContext context = assembler.assemble(request);
        return orchestrator.calculate(context);
    }

    @GetMapping("/config/attributes")
    @Operation(summary = "List declared attributes", description = "Read-only attribute dictionary for low-code UI introspection.")
    public List<AttributeConfig> attributes() {
        return configLoader.getAttributes();
    }

    @GetMapping("/config/rules")
    @Operation(summary = "List loaded eligibility and formula rules", description = "Read-only rules for low-code UI introspection.")
    public ConfigRulesView rules() {
        return new ConfigRulesView(configLoader.getEligibilityRules(), configLoader.getFormulaRules());
    }
}