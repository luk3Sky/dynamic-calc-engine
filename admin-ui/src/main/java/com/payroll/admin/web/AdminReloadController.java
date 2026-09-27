package com.payroll.admin.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The single "Reload from Disk" endpoint used by every editor page. Re-reads
 * the four JSON files and activates them, discarding any unsaved in-memory
 * edits. The UI confirms with the operator (JS confirm) before submitting.
 */
@Controller
@RequestMapping("/admin/reload")
public class AdminReloadController {

    private final AdminConfigService service;

    public AdminReloadController(AdminConfigService service) {
        this.service = service;
    }

    @PostMapping
    public String reloadFromDisk(RedirectAttributes redirect) {
        service.reloadFromDisk();
        redirect.addFlashAttribute("flash",
                "Reloaded from disk. Any unsaved in-memory edits were discarded.");
        return "redirect:/admin";
    }
}