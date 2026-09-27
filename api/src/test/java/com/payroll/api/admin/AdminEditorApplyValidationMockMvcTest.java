package com.payroll.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.payroll.common.config.PayrollConfig;
import com.payroll.common.domain.PayrollResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Per-editor MockMvc tests: a valid form followed by "Apply to Runtime" is
 * reflected in a subsequent orchestrator calculation (Drools + jEasy), while an
 * invalid form renders validation errors and never calls ConfigRuntime.reload().
 */
class AdminEditorApplyValidationMockMvcTest extends AbstractAdminMockMvcTest {

    @Test
    void eligibilityApplyChangesSubsequentCalculation() throws Exception {
        assertThat(calculate(fullTimePayload()).getNetPay()).isEqualTo(new BigDecimal("1117.50"));

        // tighten overtime eligibility to grade >= 5; the grade-4 sample is no longer eligible
        mockMvc.perform(post("/admin/eligibility-rules")
                        .param("action", "apply")
                        .param("originalRuleId", "RULE_OVERTIME_ELIGIBLE")
                        .param("ruleId", "RULE_OVERTIME_ELIGIBLE")
                        .param("description", "tighter overtime eligibility")
                        .param("conditions[0].attribute", "employmentType")
                        .param("conditions[0].operator", "EQUALS")
                        .param("conditions[0].value", "FULL_TIME")
                        .param("conditions[1].attribute", "employeeGrade")
                        .param("conditions[1].operator", "GREATER_THAN")
                        .param("conditions[1].value", "5")
                        .param("derivedFacts[0].attribute", "overtimeEligible")
                        .param("derivedFacts[0].value", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/eligibility-rules"));

        PayrollResult after = calculate(fullTimePayload());
        assertThat(after.getNetPay()).isEqualTo(new BigDecimal("982.50"));
        assertThat(after.getCalculationTrace()).noneMatch(line -> line.startsWith("RULE_OVERTIME_PAY"));
    }

    @Test
    void formulaApplyChangesSubsequentCalculation() throws Exception {
        assertThat(calculate(fullTimePayload()).getNetPay()).isEqualTo(new BigDecimal("1117.50"));

        mockMvc.perform(post("/admin/formula-rules")
                        .param("action", "apply")
                        .param("originalRuleId", "RULE_OVERTIME_PAY")
                        .param("ruleId", "RULE_OVERTIME_PAY")
                        .param("name", "Overtime Pay")
                        .param("priority", "2")
                        .param("condition", "overtimeEligible == true && overtimeHours > 0")
                        .param("formula", "hourlyRate * overtimeHours * overtimeMultiplier * 2")
                        .param("outputAttribute", "overtimePay")
                        .param("componentType", "EARNING"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/formula-rules"));

        PayrollResult after = calculate(fullTimePayload());
        assertThat(after.getNetPay()).isEqualTo(new BigDecimal("1252.50"));
    }

    @Test
    void attributesApplyReflectsInIntrospection() throws Exception {
        mockMvc.perform(get("/api/v1/payroll/config/attributes"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("hourlyRate")));

        // the attributes form posts the whole dictionary, so submit all rows with one renamed
        mockMvc.perform(attributesFormWithDisplayName("hourlyRate", "Hourly Rate (renamed)"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/attributes"));

        assertThat(configRuntime.getConfig().findAttribute("hourlyRate").orElseThrow().getDisplayName())
                .isEqualTo("Hourly Rate (renamed)");
    }

    @Test
    void workflowApplyReordersStagesInRuntime() throws Exception {
        PayrollConfig before = configRuntime.getConfig();
        assertThat(before.getStages().get(0).getName()).isEqualTo("INITIAL_ELIGIBILITY");

        // submit the full workflow with the first two stages swapped (BASE_PAY first)
        MockHttpServletRequestBuilder request = post("/admin/workflow").param("action", "apply");
        int[] order = new int[before.getStages().size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        order[0] = 1;
        order[1] = 0;
        addStages(request, before, order);

        mockMvc.perform(request)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/workflow"));

        PayrollConfig after = configRuntime.getConfig();
        assertThat(after.getStages().get(0).getName()).isEqualTo("BASE_PAY");
        assertThat(after.getStages().get(1).getName()).isEqualTo("INITIAL_ELIGIBILITY");
    }

    @Test
    void invalidEligibilityFormRendersErrorsAndDoesNotReload() throws Exception {
        PayrollConfig before = configRuntime.getConfig();

        mockMvc.perform(post("/admin/eligibility-rules")
                        .param("action", "apply")
                        .param("originalRuleId", "RULE_OVERTIME_ELIGIBLE")
                        .param("ruleId", "RULE_OVERTIME_ELIGIBLE")
                        .param("conditions[0].attribute", "ghostAttribute")
                        .param("conditions[0].operator", "EQUALS")
                        .param("conditions[0].value", "x")
                        .param("derivedFacts[0].attribute", "overtimeEligible")
                        .param("derivedFacts[0].value", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/eligibility-rules"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("not declared")));

        assertThat(configRuntime.getConfig()).isEqualTo(before);
    }

    @Test
    void invalidAttributesFormRendersErrorsAndDoesNotReload() throws Exception {
        PayrollConfig before = configRuntime.getConfig();

        mockMvc.perform(post("/admin/attributes")
                        .param("action", "apply")
                        .param("attributes[0].id", "hourlyRate")
                        .param("attributes[0].dataType", "NUMBER")
                        .param("attributes[0].source", "EMPLOYEE_MASTER")
                        .param("attributes[0].defaultValue", "0")
                        .param("attributes[1].id", "hourlyRate")
                        .param("attributes[1].dataType", "NUMBER")
                        .param("attributes[1].source", "EMPLOYEE_MASTER")
                        .param("attributes[1].defaultValue", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/attributes"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("duplicate attribute id")));

        assertThat(configRuntime.getConfig()).isEqualTo(before);
    }

    @Test
    void invalidWorkflowFormRendersErrorsAndDoesNotReload() throws Exception {
        PayrollConfig before = configRuntime.getConfig();

        mockMvc.perform(post("/admin/workflow")
                        .param("action", "apply")
                        .param("stages[0].name", "BROKEN")
                        .param("stages[0].engine", "FORMULA")
                        .param("stages[0].execution", "SEQUENTIAL")
                        .param("stages[0].ruleIds", "RULE_DOES_NOT_EXIST"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/workflow"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RULE_DOES_NOT_EXIST")));

        assertThat(configRuntime.getConfig()).isEqualTo(before);
    }

    @Test
    void invalidFormulaFormRendersErrorsAndDoesNotReload() throws Exception {
        PayrollConfig before = configRuntime.getConfig();

        mockMvc.perform(post("/admin/formula-rules")
                        .param("action", "apply")
                        .param("originalRuleId", "RULE_OVERTIME_PAY")
                        .param("ruleId", "RULE_OVERTIME_PAY")
                        .param("condition", "overtimeEligible == true")
                        .param("formula", "hourlyRate * ghostAttribute")
                        .param("outputAttribute", "overtimePay"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/formula-rules"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ghostAttribute")));

        assertThat(configRuntime.getConfig()).isEqualTo(before);
    }

    private PayrollResult calculate(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/payroll/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), PayrollResult.class);
    }

    private String fullTimePayload() throws Exception {
        return new ClassPathResource("/samples/full-time-overtime.json")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    /** Adds every stage to the workflow form in the given permuted index order. */
    private void addStages(MockHttpServletRequestBuilder request, PayrollConfig config, int[] order) {
        var stages = config.getStages();
        for (int i = 0; i < order.length; i++) {
            var stage = stages.get(order[i]);
            request.param("stages[" + i + "].name", stage.getName());
            if (stage.getDescription() != null) {
                request.param("stages[" + i + "].description", stage.getDescription());
            }
            request.param("stages[" + i + "].engine", stage.getEngine().name());
            request.param("stages[" + i + "].execution", stage.getExecution().name());
            for (String ruleId : stage.getRuleIds()) {
                request.param("stages[" + i + "].ruleIds", ruleId);
            }
        }
    }

    /** Full attributes form (all rows) with one attribute's displayName overridden. */
    private MockHttpServletRequestBuilder attributesFormWithDisplayName(String id, String displayName) {
        MockHttpServletRequestBuilder request = post("/admin/attributes").param("action", "apply");
        var attributes = configRuntime.getConfig().getAttributes();
        for (int i = 0; i < attributes.size(); i++) {
            var attribute = attributes.get(i);
            request.param("attributes[" + i + "].id", attribute.getId())
                    .param("attributes[" + i + "].displayName",
                            attribute.getId().equals(id) ? displayName : attribute.getDisplayName())
                    .param("attributes[" + i + "].dataType", attribute.getDataType().name())
                    .param("attributes[" + i + "].source", attribute.getSource().name())
                    .param("attributes[" + i + "].defaultValue", defaultValue(attribute));
        }
        return request;
    }

    private String defaultValue(com.payroll.common.config.AttributeConfig attribute) {
        if (attribute.getDefaultValue() == null || attribute.getDefaultValue().isNull()) {
            return "";
        }
        if (attribute.getDefaultValue().isNumber()) {
            return attribute.getDefaultValue().decimalValue().toPlainString();
        }
        return attribute.getDefaultValue().asText();
    }
}