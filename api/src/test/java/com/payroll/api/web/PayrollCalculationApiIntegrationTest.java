package com.payroll.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.api.PayrollApplication;
import com.payroll.common.domain.PayrollResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for POST /api/v1/payroll/calculate using the three sample
 * payloads, exact to-the-cent hand-computed values, plus 400 validation and
 * fail-fast startup behaviour.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PayrollCalculationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullTimeWithOvertime() throws Exception {
        // BASE_PAY 25.00*40=1000.00; OVERTIME 25.00*4*1.5=150.00; ALLOWANCES
        // 150.00+75.00=225.00 -> GROSS 1375.00; SLAB_2 10% -> TAX 137.50;
        // PF 1000.00*0.12=120.00 -> NET 1375.00-257.50 = 1117.50
        PayrollResult result = postAndParse("/samples/full-time-overtime.json");

        assertThat(result.getEmployeeId()).isEqualTo("EMP-1001");
        assertThat(result.getGrossPay()).isEqualTo(new BigDecimal("1375.00"));
        assertThat(result.getTotalDeductions()).isEqualTo(new BigDecimal("257.50"));
        assertThat(result.getNetPay()).isEqualTo(new BigDecimal("1117.50"));
        assertThat(result.getComponents()).extracting(c -> c.getSourceRule())
                .containsExactly("RULE_BASE_PAY", "RULE_OVERTIME_PAY",
                        "RULE_TRANSPORT_ALLOWANCE", "RULE_MEAL_ALLOWANCE",
                        "RULE_INCOME_TAX", "RULE_PROVIDENT_FUND");
        assertThat(result.getCalculationTrace()).isNotEmpty()
                .anyMatch(line -> line.startsWith("RULE_OVERTIME_PAY"))
                .anyMatch(line -> line.startsWith("RULE_TAX_SLAB_MID"));
    }

    @Test
    void partTimeNoOvertime() throws Exception {
        // BASE_PAY 20.00*20=400.00; no overtime/activity/allowances; SLAB_1 0%
        // tax; no PF (PART_TIME) -> NET 400.00
        PayrollResult result = postAndParse("/samples/part-time-no-overtime.json");

        assertThat(result.getGrossPay()).isEqualTo(new BigDecimal("400.00"));
        assertThat(result.getTotalDeductions()).isEqualTo(new BigDecimal("0.00"));
        assertThat(result.getNetPay()).isEqualTo(new BigDecimal("400.00"));
        assertThat(result.getComponents()).extracting(c -> c.getSourceRule())
                .containsExactly("RULE_BASE_PAY");
        assertThat(result.getCalculationTrace()).isNotEmpty();
    }

    @Test
    void activityContractor() throws Exception {
        // ACTIVITY 500*2.50=1250.00; GROSS 1250.00; SLAB_2 10% -> TAX 125.00;
        // no PF (CONTRACT) -> NET 1250.00-125.00 = 1125.00
        PayrollResult result = postAndParse("/samples/activity-based-contractor.json");

        assertThat(result.getGrossPay()).isEqualTo(new BigDecimal("1250.00"));
        assertThat(result.getTotalDeductions()).isEqualTo(new BigDecimal("125.00"));
        assertThat(result.getNetPay()).isEqualTo(new BigDecimal("1125.00"));
        assertThat(result.getComponents()).extracting(c -> c.getSourceRule())
                .containsExactly("RULE_ACTIVITY_PAY", "RULE_INCOME_TAX");
        assertThat(result.getCalculationTrace()).isNotEmpty();
    }

    @Test
    void incompletePayloadReturns400WithReadableMessage() throws Exception {
        String body = """
                {
                  "employeeId": "EMP-1001",
                  "period": { "startDate": "2026-09-01", "endDate": "2026-09-30" },
                  "attendance": { "standardHours": 40, "overtimeHours": 0 }
                }
                """;

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/payroll/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn();

        String json = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json).contains("Bad Request").contains("employee");
    }

    @Test
    void invalidDateRangeReturns400() throws Exception {
        String body = """
                {
                  "employeeId": "EMP-1001",
                  "period": { "startDate": "2026-10-01", "endDate": "2026-09-01" },
                  "employee": { "grade": 4, "employmentType": "FULL_TIME" },
                  "attendance": { "standardHours": 40, "overtimeHours": 0 }
                }
                """;

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/payroll/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("period.startDate must not be after period.endDate");
    }

    @Test
    void inconsistentConfigFailsFastAtStartup() {
        // @SpringBootApplication would fail to start (not at request time) when
        // the workflow references an unknown formula ruleId.
        new ApplicationContextRunner()
                .withPropertyValues("payroll.config.location=classpath:broken-config/")
                .withUserConfiguration(PayrollApplication.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("RULE_DOES_NOT_EXIST");
                });
    }

    private PayrollResult postAndParse(String resourcePath) throws Exception {
        String body = new ClassPathResource(resourcePath).getContentAsString(StandardCharsets.UTF_8);
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/payroll/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8), PayrollResult.class);
    }
}