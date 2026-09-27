package com.payroll.admin.web;

import com.payroll.admin.persistence.ConfigFileStore;
import com.payroll.common.config.ConfigLoaderService;
import com.payroll.common.config.ConfigSection;
import com.payroll.common.config.PayrollConfig;
import com.payroll.rulesengine.config.ConfigRuntime;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Admin dashboard: counts of loaded attributes / rules / stages, whether the
 * in-memory config diverges from what is on disk, and the last-saved timestamp
 * per config file.
 */
@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ConfigRuntime runtime;
    private final ConfigLoaderService configLoader;
    private final ConfigFileStore fileStore;
    private final String configLocation;

    public AdminDashboardController(ConfigRuntime runtime, ConfigLoaderService configLoader,
            ConfigFileStore fileStore,
            @Value("${payroll.config.location:classpath:sample-config/}") String configLocation) {
        this.runtime = runtime;
        this.configLoader = configLoader;
        this.fileStore = fileStore;
        this.configLocation = configLocation;
    }

    @GetMapping
    public String dashboard(Model model) {
        PayrollConfig live = runtime.getConfig();
        PayrollConfig onDisk = configLoader.loadConfig();
        boolean dirty = !live.equals(onDisk);

        model.addAttribute("activeNav", "dashboard");
        model.addAttribute("config", live);
        model.addAttribute("dirty", dirty);
        model.addAttribute("configLocation", configLocation);
        model.addAttribute("writable", fileStore.isWritable());
        model.addAttribute("attributeCount", live.getAttributes().size());
        model.addAttribute("eligibilityRuleCount", live.getEligibilityRules().size());
        model.addAttribute("formulaRuleCount", live.getFormulaRules().size());
        model.addAttribute("stageCount", live.getStages().size());

        Map<String, String> lastSaved = new java.util.HashMap<>();
        for (ConfigSection section : ConfigSection.values()) {
            Instant instant = fileStore.lastSavedAt(section);
            lastSaved.put(section.name(), instant == null ? "—" : TIMESTAMP.format(instant));
        }
        model.addAttribute("lastSaved", lastSaved);
        return "admin/dashboard";
    }
}