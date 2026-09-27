package com.payroll.api.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.payroll.api.PayrollApplication;
import com.payroll.common.config.AttributeConfig;
import com.payroll.common.config.JacksonConfigLoaderService;
import com.payroll.common.config.PayrollConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Smoke test proving the admin console pages render (Thymeleaf templates
 * resolve) inside the api application context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AdminConsoleSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardRenders() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"));
    }

    @Test
    void attributesEditorRenders() throws Exception {
        mockMvc.perform(get("/admin/attributes"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/attributes"));
    }

    @Test
    void attributesPostWithValidateActionBindsAndValidates() throws Exception {
        // the form posts the WHOLE attribute table, so submit the full current set
        mockMvc.perform(attributesForm("validate"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/attributes"));
    }

    @Test
    void eligibilityEditorRenders() throws Exception {
        mockMvc.perform(get("/admin/eligibility-rules"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/eligibility-rules"));
    }

    @Test
    void eligibilityRuleFormPostsAndApplies() throws Exception {
        mockMvc.perform(post("/admin/eligibility-rules")
                        .param("action", "apply")
                        .param("originalRuleId", "RULE_OVERTIME_ELIGIBLE")
                        .param("ruleId", "RULE_OVERTIME_ELIGIBLE")
                        .param("description", "Full-time employees at grade 4 or above qualify for overtime premium pay.")
                        .param("conditions[0].attribute", "employmentType")
                        .param("conditions[0].operator", "EQUALS")
                        .param("conditions[0].value", "FULL_TIME")
                        .param("conditions[1].attribute", "employeeGrade")
                        .param("conditions[1].operator", "GREATER_THAN")
                        .param("conditions[1].value", "3")
                        .param("derivedFacts[0].attribute", "overtimeEligible")
                        .param("derivedFacts[0].value", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/eligibility-rules"));
    }

    @Test
    void formulaRulesEditorRenders() throws Exception {
        mockMvc.perform(get("/admin/formula-rules"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/formula-rules"));
    }

    @Test
    void formulaRuleTestExpressionEvaluates() throws Exception {
        mockMvc.perform(post("/admin/formula-rules/test")
                        .param("ruleId", "RULE_BASE_PAY")
                        .param("condition", "standardHours > 0")
                        .param("formula", "hourlyRate * standardHours"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/formula-rules"));
    }

    @Test
    void formulaRuleFormPostsAndApplies() throws Exception {
        mockMvc.perform(post("/admin/formula-rules")
                        .param("action", "apply")
                        .param("originalRuleId", "RULE_BASE_PAY")
                        .param("ruleId", "RULE_BASE_PAY")
                        .param("name", "Base Pay")
                        .param("description", "Regular pay for standard hours worked.")
                        .param("priority", "1")
                        .param("condition", "standardHours > 0")
                        .param("formula", "hourlyRate * standardHours")
                        .param("outputAttribute", "basePay")
                        .param("componentType", "EARNING"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/formula-rules"));
    }

    @Test
    void workflowEditorRenders() throws Exception {
        mockMvc.perform(get("/admin/workflow"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/workflow"));
    }

    @Test
    void workflowFormPostsAndApplies() throws Exception {
        mockMvc.perform(post("/admin/workflow")
                        .param("action", "apply")
                        .param("stages[0].name", "INITIAL_ELIGIBILITY")
                        .param("stages[0].description", "Classify overtime and activity-bonus eligibility before any formula runs.")
                        .param("stages[0].engine", "ELIGIBILITY")
                        .param("stages[0].execution", "SEQUENTIAL")
                        .param("stages[0].ruleIds", "RULE_OVERTIME_ELIGIBLE")
                        .param("stages[0].ruleIds", "RULE_ACTIVITY_BONUS_ELIGIBLE")
                        .param("stages[1].name", "BASE_PAY")
                        .param("stages[1].description", "Compute regular base pay.")
                        .param("stages[1].engine", "FORMULA")
                        .param("stages[1].execution", "SEQUENTIAL")
                        .param("stages[1].ruleIds", "RULE_BASE_PAY")
                        .param("stages[2].name", "OVERTIME")
                        .param("stages[2].description", "Compute overtime premium pay.")
                        .param("stages[2].engine", "FORMULA")
                        .param("stages[2].execution", "SEQUENTIAL")
                        .param("stages[2].ruleIds", "RULE_OVERTIME_PAY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/workflow"));
    }

    private MockHttpServletRequestBuilder attributesForm(String action) {
        PayrollConfig config = new JacksonConfigLoaderService().loadConfig();
        MockHttpServletRequestBuilder request = post("/admin/attributes").param("action", action);
        List<AttributeConfig> attributes = config.getAttributes();
        for (int i = 0; i < attributes.size(); i++) {
            AttributeConfig attribute = attributes.get(i);
            request
                    .param("attributes[" + i + "].id", attribute.getId())
                    .param("attributes[" + i + "].displayName", attribute.getDisplayName())
                    .param("attributes[" + i + "].dataType", attribute.getDataType().name())
                    .param("attributes[" + i + "].source", attribute.getSource().name())
                    .param("attributes[" + i + "].defaultValue", defaultValue(attribute));
        }
        return request;
    }

    private String defaultValue(AttributeConfig attribute) {
        if (attribute.getDefaultValue() == null || attribute.getDefaultValue().isNull()) {
            return "";
        }
        if (attribute.getDefaultValue().isNumber()) {
            return attribute.getDefaultValue().decimalValue().toPlainString();
        }
        return attribute.getDefaultValue().asText();
    }
}