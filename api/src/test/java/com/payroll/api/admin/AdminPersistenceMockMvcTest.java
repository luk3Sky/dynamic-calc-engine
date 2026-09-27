package com.payroll.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.payroll.common.domain.PayrollResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Persistence-action MockMvc tests: Save to File writes pretty-printed JSON to
 * the original temp-dir files with a rolling .bak (prior content recoverable),
 * invalid configs never reach disk, and Reload from Disk discards an unsaved
 * in-memory edit.
 */
class AdminPersistenceMockMvcTest extends AbstractAdminMockMvcTest {

    @Test
    void saveToFileWritesPrettyPrintedJsonPlusRollingBackup() throws Exception {
        String originalFormulaFile = readConfigFile("formula-rules.json");
        assertThat(originalFormulaFile).contains("hourlyRate * overtimeHours * overtimeMultiplier");

        mockMvc.perform(modifiedFormulaForm("save", "hourlyRate * overtimeHours * overtimeMultiplier * 2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/formula-rules"));

        String savedFile = readConfigFile("formula-rules.json");
        String backupFile = readConfigFile("formula-rules.json.bak");

        // main file holds the new formula, .bak holds the previous content
        assertThat(savedFile).contains("hourlyRate * overtimeHours * overtimeMultiplier * 2");
        assertThat(backupFile).isEqualTo(originalFormulaFile);

        // pretty-printed, valid JSON for all four files
        for (String file : new String[] {"attributes.json", "eligibility-rules.json", "formula-rules.json", "workflow.json"}) {
            String json = readConfigFile(file);
            assertThat(json).contains("\n");   // pretty-printed
            assertThat(objectMapper.readTree(json)).isNotNull();
        }
    }

    @Test
    void secondSaveRollsPreviousContentIntoBackup() throws Exception {
        mockMvc.perform(modifiedFormulaForm("save", "hourlyRate * overtimeHours * overtimeMultiplier * 2"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(modifiedFormulaForm("save", "hourlyRate * overtimeHours * overtimeMultiplier * 3"))
                .andExpect(status().is3xxRedirection());

        // main file holds the newest content; .bak holds the previous main content (one-step safety net)
        assertThat(readConfigFile("formula-rules.json")).contains("* 3");
        assertThat(readConfigFile("formula-rules.json.bak")).contains("* 2");
    }

    @Test
    void invalidConfigHardBlocksSaveAndWritesNothing() throws Exception {
        String before = readConfigFile("formula-rules.json");

        mockMvc.perform(post("/admin/formula-rules")
                        .param("action", "save")
                        .param("originalRuleId", "RULE_OVERTIME_PAY")
                        .param("ruleId", "RULE_OVERTIME_PAY")
                        .param("condition", "overtimeEligible == true")
                        .param("formula", "hourlyRate * ghostAttribute")
                        .param("outputAttribute", "overtimePay"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/formula-rules"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("ghostAttribute"));

        assertThat(readConfigFile("formula-rules.json")).isEqualTo(before);
        assertThat(configRuntime.getConfig())
                .isEqualTo(new com.payroll.common.config.JacksonConfigLoaderService().loadConfig());
    }

    @Test
    void reloadFromDiskDiscardsUnsavedInMemoryEdit() throws Exception {
        assertThat(calculateNet(fullTimePayload())).isEqualByComparingTo(new BigDecimal("1117.50"));

        // apply a formula edit to the runtime (memory only, nothing on disk)
        mockMvc.perform(modifiedFormulaForm("apply", "hourlyRate * overtimeHours * overtimeMultiplier * 2"))
                .andExpect(status().is3xxRedirection());
        assertThat(calculateNet(fullTimePayload())).isEqualByComparingTo(new BigDecimal("1252.50"));

        // reload from disk discards the unsaved edit
        mockMvc.perform(post("/admin/reload"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        assertThat(calculateNet(fullTimePayload())).isEqualByComparingTo(new BigDecimal("1117.50"));
        assertThat(configRuntime.getConfig())
                .isEqualTo(new com.payroll.common.config.JacksonConfigLoaderService().loadConfig());
    }

    private BigDecimal calculateNet(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/payroll/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8), PayrollResult.class).getNetPay();
    }

    private String fullTimePayload() throws Exception {
        return new ClassPathResource("/samples/full-time-overtime.json")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private MockHttpServletRequestBuilder modifiedFormulaForm(String action, String formula) {
        return post("/admin/formula-rules")
                .param("action", action)
                .param("originalRuleId", "RULE_OVERTIME_PAY")
                .param("ruleId", "RULE_OVERTIME_PAY")
                .param("name", "Overtime Pay")
                .param("description", "Premium pay at the configured multiplier for eligible overtime hours.")
                .param("priority", "2")
                .param("condition", "overtimeEligible == true && overtimeHours > 0")
                .param("formula", formula)
                .param("outputAttribute", "overtimePay")
                .param("componentType", "EARNING");
    }
}