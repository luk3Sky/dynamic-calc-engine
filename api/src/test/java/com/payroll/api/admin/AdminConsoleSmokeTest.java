package com.payroll.api.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Smoke test proving every admin console page renders (Thymeleaf templates
 * resolve) inside the api application context. GET-only: mutating behaviour is
 * covered by the dedicated MockMvc tests in this package.
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
    void eligibilityEditorRenders() throws Exception {
        mockMvc.perform(get("/admin/eligibility-rules"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/eligibility-rules"));
    }

    @Test
    void formulaRulesEditorRenders() throws Exception {
        mockMvc.perform(get("/admin/formula-rules"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/formula-rules"));
    }

    @Test
    void workflowEditorRenders() throws Exception {
        mockMvc.perform(get("/admin/workflow"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/workflow"));
    }
}