package com.payroll.admin.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.payroll.common.config.AttributeCatalog;
import com.payroll.common.config.ConfigSection;
import com.payroll.common.config.ConfigValidationException;
import com.payroll.common.config.EligibilityRulesConfig;
import com.payroll.common.config.FormulaRulesConfig;
import com.payroll.common.config.PayrollConfig;
import com.payroll.common.config.WorkflowConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Persists the in-memory config back to its original JSON files. Only active
 * when the config was loaded from a filesystem path — a classpath-loaded config
 * (the default) cannot be written, and the "Save to File" action then fails
 * with a clear message instead of silently doing nothing.
 *
 * <p>Before overwriting a file its previous content is copied to a single
 * rolling {@code .bak} next to it (not a versioned history — a one-step safety
 * net so a bad save can always be recovered from the backup).
 */
@Component
public class ConfigFileStore {

    private static final Map<ConfigSection, String> FILE_NAMES = Map.of(
            ConfigSection.ATTRIBUTES, "attributes.json",
            ConfigSection.ELIGIBILITY_RULES, "eligibility-rules.json",
            ConfigSection.FORMULA_RULES, "formula-rules.json",
            ConfigSection.WORKFLOW, "workflow.json");

    private final boolean writable;
    private final Map<ConfigSection, Path> paths = new EnumMap<>(ConfigSection.class);
    private final Map<ConfigSection, Instant> lastSaved = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public ConfigFileStore(
            @Value("${payroll.config.location:classpath:sample-config/}") String configLocation) {
        this.mapper = new ObjectMapper()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        if (configLocation.startsWith("classpath:")) {
            this.writable = false;
        } else {
            this.writable = true;
            Path dir = Paths.get(configLocation);
            FILE_NAMES.forEach((section, file) -> paths.put(section, dir.resolve(file)));
        }
    }

    /** Whether the four config files live on the filesystem and can be written. */
    public boolean isWritable() {
        return writable;
    }

    public Path path(ConfigSection section) {
        return paths.get(section);
    }

    public Instant lastSavedAt(ConfigSection section) {
        return lastSaved.get(section);
    }

    /**
     * Writes all four JSON files (pretty-printed) from the given config,
     * rolling the previous content of each file into a {@code .bak} backup
     * first.
     */
    public void save(PayrollConfig config) {
        if (!writable) {
            throw new ConfigValidationException(
                    "Save to File requires a filesystem payroll.config.location; the current location is "
                            + "classpath-based and cannot be written.");
        }
        saveFile(ConfigSection.ATTRIBUTES,
                AttributeCatalog.builder().attributes(config.getAttributes()).build());
        saveFile(ConfigSection.ELIGIBILITY_RULES,
                EligibilityRulesConfig.builder().rules(config.getEligibilityRules()).build());
        saveFile(ConfigSection.FORMULA_RULES,
                FormulaRulesConfig.builder().rules(config.getFormulaRules()).build());
        saveFile(ConfigSection.WORKFLOW,
                WorkflowConfig.builder().stages(config.getStages()).build());
    }

    private void saveFile(ConfigSection section, Object payload) {
        Path path = paths.get(section);
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Path backup = path.resolveSibling(path.getFileName() + ".bak");
            if (Files.exists(path)) {
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            Files.writeString(path, json, StandardCharsets.UTF_8);
            lastSaved.put(section, Instant.now());
        } catch (IOException e) {
            throw new ConfigValidationException(
                    "Failed to save " + section.getFileName() + " (" + path + "): " + e.getMessage(), e);
        }
    }
}