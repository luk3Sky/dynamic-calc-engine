package com.payroll.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payroll.common.config.ConfigLoaderService;
import com.payroll.rulesengine.config.ConfigRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for the admin-console MockMvc tests. The config files live in a
 * persistent temp directory (so Save to File is writable) created when the base
 * class loads — deliberately not a JUnit {@code @TempDir}, whose per-class
 * lifecycle is fragile when several test classes share one JVM and a Spring
 * context. Before every test a pristine copy of the sample config is restored
 * and re-activated, so tests never leak edits into each other.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
abstract class AbstractAdminMockMvcTest {

    private static final List<String> CONFIG_FILES = List.of(
            "attributes.json", "eligibility-rules.json", "formula-rules.json", "workflow.json");

    static final Path configDir = createConfigDir();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ConfigRuntime configRuntime;

    @Autowired
    protected ConfigLoaderService configLoader;

    @Autowired
    protected ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configLocation(DynamicPropertyRegistry registry) throws Exception {
        // copy the sample config before the context (and its ConfigLoaderService) starts
        copySampleConfig();
        registry.add("payroll.config.location", configDir::toString);
    }

    @BeforeEach
    void restorePristineConfig() throws Exception {
        // restore a pristine copy of the sample config and activate it in the runtime
        copySampleConfig();
        configRuntime.reload(configLoader.reload());
    }

    private static Path createConfigDir() {
        try {
            return Files.createTempDirectory("payroll-admin-config");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copySampleConfig() throws Exception {
        for (String file : CONFIG_FILES) {
            try (InputStream in = AbstractAdminMockMvcTest.class.getClassLoader()
                    .getResourceAsStream("sample-config/" + file)) {
                if (in == null) {
                    throw new IllegalStateException("Missing sample config resource: " + file);
                }
                Files.copy(in, configDir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    protected String readConfigFile(String fileName) throws Exception {
        return Files.readString(configDir.resolve(fileName));
    }
}