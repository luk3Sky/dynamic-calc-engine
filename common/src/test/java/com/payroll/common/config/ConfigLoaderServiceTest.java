package com.payroll.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the {@link ConfigLoaderService} loads and validates the four sample
 * JSON files with zero errors, and that an internally inconsistent fixture
 * fails fast with an actionable message.
 */
class ConfigLoaderServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void loadsAllSampleConfigFilesWithZeroValidationErrors() {
        ConfigLoaderService loader = new JacksonConfigLoaderService();

        PayrollConfig config = loader.loadConfig();

        assertThat(config.getAttributes()).hasSize(27);
        assertThat(config.getEligibilityRules()).hasSize(5);
        assertThat(config.getFormulaRules()).hasSize(11);
        assertThat(config.getStages()).hasSize(9);

        // every workflow ruleId must resolve in the owning engine's rule set
        for (WorkflowStage stage : config.getStages()) {
            for (String ruleId : stage.getRuleIds()) {
                if (stage.getEngine() == EngineType.ELIGIBILITY) {
                    assertThat(config.findEligibilityRule(ruleId))
                            .as("workflow stage %s -> eligibility rule %s", stage.getName(), ruleId)
                            .isPresent();
                } else {
                    assertThat(config.findFormulaRule(ruleId))
                            .as("workflow stage %s -> formula rule %s", stage.getName(), ruleId)
                            .isPresent();
                }
            }
        }
    }

    @Test
    void failsFastOnUnknownFormulaRuleIdInWorkflow(@TempDir Path dir) throws Exception {
        write(dir, "attributes.json", "{\"attributes\":[{\"id\":\"grossTotal\",\"dataType\":\"NUMBER\",\"source\":\"COMPUTED\"}]}");
        write(dir, "eligibility-rules.json", "{\"rules\":[]}");
        write(dir, "formula-rules.json", "{\"rules\":[]}");
        write(dir, "workflow.json",
                "{\"stages\":[{\"name\":\"S\",\"engine\":\"FORMULA\",\"ruleIds\":[\"RULE_DOES_NOT_EXIST\"],\"execution\":\"SEQUENTIAL\"}]}");

        assertThatThrownBy(() -> new JacksonConfigLoaderService(dir.toString()))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("RULE_DOES_NOT_EXIST")
                .hasMessageContaining("S");
    }

    @Test
    void failsFastOnUndeclaredAttributeInEligibilityRule(@TempDir Path dir) throws Exception {
        write(dir, "attributes.json", "{\"attributes\":[]}");
        write(dir, "eligibility-rules.json",
                "{\"rules\":[{\"ruleId\":\"RULE_GHOST\",\"conditions\":[{\"attribute\":\"ghostAttribute\",\"operator\":\"EQUALS\",\"value\":1}],\"derivedFacts\":{\"x\":true}}]}");
        write(dir, "formula-rules.json", "{\"rules\":[]}");
        write(dir, "workflow.json",
                "{\"stages\":[{\"name\":\"S\",\"engine\":\"ELIGIBILITY\",\"ruleIds\":[\"RULE_GHOST\"],\"execution\":\"SEQUENTIAL\"}]}");

        assertThatThrownBy(() -> new JacksonConfigLoaderService(dir.toString()))
                .isInstanceOf(ConfigValidationException.class)
                .hasMessageContaining("ghostAttribute");
    }

    private void write(Path dir, String fileName, String content) throws Exception {
        Files.writeString(dir.resolve(fileName), content);
    }
}