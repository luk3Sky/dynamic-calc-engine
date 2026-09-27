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