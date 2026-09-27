package com.payroll.admin.web;

import com.payroll.admin.web.form.WorkflowForm;
import com.payroll.admin.web.form.WorkflowForm.StageRow;
import com.payroll.common.config.EngineType;
import com.payroll.common.config.PayrollConfig;
import com.payroll.common.config.StageExecution;
import com.payroll.common.config.WorkflowStage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Workflow editor: an ordered list of stages (name / engine / execution /
 * ruleIds multi-select populated from the current in-memory rules) with
 * server-side up/down reordering — no drag-and-drop library. The submitted form
 * order is the stage order. Validate must catch a ruleId that does not exist in
 * the owning engine's rule set and a stage left with no ruleIds — both come
 * from ConfigValidator.
 */
@Controller
@RequestMapping("/admin/workflow")
public class AdminWorkflowController extends AdminSectionController {

    private static final String PAGE = "/admin/workflow";

    public AdminWorkflowController(AdminConfigService service) {
        super(service);
    }

    @GetMapping
    public String editor(Model model) {
        PayrollConfig config = service.currentConfig();
        model.addAttribute("workflowForm", toForm(config));
        populate(config, "workflow", model);
        return "admin/workflow";
    }

    @PostMapping
    public String submit(@ModelAttribute("workflowForm") WorkflowForm form,
            @RequestParam("action") String action,
            Model model, RedirectAttributes redirect) {
        PayrollConfig current = service.currentConfig();

        if (action.startsWith("moveUp:")) {
            move(form, Integer.parseInt(action.substring("moveUp:".length())), -1);
            model.addAttribute("workflowForm", form);
            populate(current, "workflow", model);
            return "admin/workflow";
        }
        if (action.startsWith("moveDown:")) {
            move(form, Integer.parseInt(action.substring("moveDown:".length())), +1);
            model.addAttribute("workflowForm", form);
            populate(current, "workflow", model);
            return "admin/workflow";
        }

        PayrollConfig candidate = service.withStages(toConfig(form));
        String redirectView = resolve(PAGE, candidate, action, model, redirect);
        if (redirectView == null) {
            model.addAttribute("workflowForm", form);
            populate(current, "workflow", model);
            return "admin/workflow";
        }
        return redirectView;
    }

    private void move(WorkflowForm form, int index, int delta) {
        List<StageRow> stages = form.getStages();
        int target = index + delta;
        if (index < 0 || index >= stages.size() || target < 0 || target >= stages.size()) {
            return;
        }
        Collections.swap(stages, index, target);
    }

    private List<WorkflowStage> toConfig(WorkflowForm form) {
        List<WorkflowStage> stages = new ArrayList<>();
        for (StageRow row : form.getStages()) {
            if (row == null || row.isDeleted() || isBlankRow(row)) {
                continue;
            }
            stages.add(WorkflowStage.builder()
                    .name(trimToNull(row.getName()))
                    .description(trimToNull(row.getDescription()))
                    .engine(parseEnum(EngineType.class, row.getEngine()))
                    .execution(parseEnum(StageExecution.class, row.getExecution()))
                    .ruleIds(row.getRuleIds() == null ? List.of()
                            : row.getRuleIds().stream().filter(r -> !isBlank(r)).map(String::trim).toList())
                    .build());
        }
        return stages;
    }

    private boolean isBlankRow(StageRow row) {
        return isBlank(row.getName()) && isBlank(row.getEngine()) && isBlank(row.getExecution())
                && (row.getRuleIds() == null || row.getRuleIds().stream().allMatch(AdminWorkflowController::isBlank));
    }

    private WorkflowForm toForm(PayrollConfig config) {
        WorkflowForm form = new WorkflowForm();
        for (WorkflowStage stage : config.getStages()) {
            StageRow row = new StageRow();
            row.setName(stage.getName());
            row.setDescription(stage.getDescription());
            row.setEngine(stage.getEngine() == null ? null : stage.getEngine().name());
            row.setExecution(stage.getExecution() == null ? null : stage.getExecution().name());
            row.setRuleIds(stage.getRuleIds() == null ? List.of() : new ArrayList<>(stage.getRuleIds()));
            form.getStages().add(row);
        }
        return form;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}