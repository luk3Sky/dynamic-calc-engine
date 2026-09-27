package com.payroll.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Jackson-backed {@link ConfigLoaderService} that loads the four configuration
 * files from a classpath or file-system location and validates them eagerly in
 * the constructor (fail fast at startup).
 *
 * <p>Configuration location formats: {@code classpath:path/to/dir/} or an
 * absolute/relative filesystem path.
 */
public class JacksonConfigLoaderService implements ConfigLoaderService {

    public static final String DEFAULT_LOCATION = "classpath:sample-config/";

    private static final String ATTRIBUTES = "attributes.json";
    private static final String ELIGIBILITY_RULES = "eligibility-rules.json";
    private static final String FORMULA_RULES = "formula-rules.json";
    private static final String WORKFLOW = "workflow.json";

    private final PayrollConfig config;

    public JacksonConfigLoaderService() {
        this(DEFAULT_LOCATION);
    }

    public JacksonConfigLoaderService(String configLocation) {
        this.config = readAndValidate(configLocation);
    }

    private PayrollConfig readAndValidate(String location) {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

            List<AttributeConfig> attributes =
                    mapper.readValue(open(location, ATTRIBUTES), AttributeCatalog.class).getAttributes();
            List<EligibilityRuleConfig> eligibilityRules =
                    mapper.readValue(open(location, ELIGIBILITY_RULES), EligibilityRulesConfig.class).getRules();
            List<FormulaRuleConfig> formulaRules =
                    mapper.readValue(open(location, FORMULA_RULES), FormulaRulesConfig.class).getRules();
            List<WorkflowStage> stages =
                    mapper.readValue(open(location, WORKFLOW), WorkflowConfig.class).getStages();

            PayrollConfig config = PayrollConfig.builder()
                    .attributes(attributes)
                    .eligibilityRules(eligibilityRules)
                    .formulaRules(formulaRules)
                    .stages(stages)
                    .build();
            PayrollConfigValidator.validate(config);
            return config;
        } catch (ConfigValidationException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new ConfigValidationException(
                    "Failed to parse payroll config from '" + location + "': " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new ConfigValidationException(
                    "Failed to read payroll config from '" + location + "': " + e.getMessage(), e);
        }
    }

    private InputStream open(String location, String fileName) throws IOException {
        if (location.startsWith("classpath:")) {
            String path = location.substring("classpath:".length());
            if (!path.endsWith("/")) {
                path += "/";
            }
            path += fileName;
            InputStream in = JacksonConfigLoaderService.class.getClassLoader().getResourceAsStream(path);
            if (in == null) {
                throw new ConfigValidationException("Payroll config file not found on classpath: " + path);
            }
            return in;
        }
        Path dir = Paths.get(location);
        Path file = dir.resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new ConfigValidationException("Payroll config file not found: " + file.toAbsolutePath());
        }
        return Files.newInputStream(file);
    }

    @Override
    public PayrollConfig loadConfig() {
        return config;
    }
}