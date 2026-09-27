package com.payroll.admin.web;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.payroll.admin.web.form.FormulaRuleForm;
import com.payroll.common.config.AttributeConfig;
import com.payroll.common.config.AttributeDataType;
import com.payroll.common.config.AttributeSource;
import com.payroll.common.config.FormulaRuleConfig;
import com.payroll.common.config.PayrollConfig;
import com.payroll.common.domain.CalculationContext;
import com.payroll.common.domain.ComponentType;
import com.payroll.rulesengine.orchestration.ContextDefaults;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.mvel2.MVEL;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Formula rules editor (jEasy-backed). Per-rule expand-to-edit with MVEL
 * condition/formula text areas, a "Test Expression" action that evaluates both
 * against a sample CalculationContext built from current attribute defaults and
 * reports the result or a specific MVEL error inline, and an output-attribute
 * dropdown that can declare a brand-new COMPUTED attribute in the same edit.
 */
@Controller
@RequestMapping("/admin/formula-rules")
public class AdminFormulaRulesController extends AdminSectionController {

    private static final String PAGE = "/admin/formula-rules";
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    public AdminFormulaRulesController(AdminConfigService service) {
        super(service);
    }

    @GetMapping
    public String editor(Model model) {
        PayrollConfig config = service.currentConfig();
        model.addAttribute("ruleForms", config.getFormulaRules().stream().map(this::toForm).toList());
        model.addAttribute("newRuleForm", new FormulaRuleForm());
        populate(config, "formula", model);
        return "admin/formula-rules";
    }

    @PostMapping
    public String submit(@ModelAttribute("ruleForm") FormulaRuleForm form,
            @RequestParam("action") String action,
            Model model, RedirectAttributes redirect) {
        PayrollConfig current = service.currentConfig();
        PayrollConfig candidate = buildCandidate(form, current);

        String redirectView = resolve(PAGE, candidate, action, model, redirect);
        if (redirectView == null) {
            model.addAttribute("ruleForms", reRenderedForms(form, current));
            model.addAttribute("newRuleForm", new FormulaRuleForm());
            populate(current, "formula", model);
            return "admin/formula-rules";
        }
        return redirectView;
    }

    @PostMapping("/test")
    public String testExpression(@ModelAttribute("ruleForm") FormulaRuleForm form, Model model) {
        PayrollConfig current = service.currentConfig();
        CalculationContext sample = ContextDefaults.apply(new CalculationContext(), current);
        Map<String, Object> bridge = sample.getAttributes();

        StringBuilder result = new StringBuilder();
        String error = null;

        if (!isBlank(form.getCondition())) {
            try {
                Object evaluated = MVEL.eval(form.getCondition(), bridge);
                result.append("condition → ").append(evaluated).append("\n");
            } catch (RuntimeException e) {
                error = "Condition MVEL error: " + mvelMessage(e);
            }
        }
        if (error == null && !isBlank(form.getFormula())) {
            try {
                Object evaluated = MVEL.eval(form.getFormula(), bridge);
                result.append("formula → ").append(evaluated).append("\n");
            } catch (RuntimeException e) {
                error = "Formula MVEL error: " + mvelMessage(e);
            }
        }

        model.addAttribute("testResult", result.toString().stripTrailing());
        model.addAttribute("testError", error);
        model.addAttribute("ruleForms", reRenderedForms(form, current));
        model.addAttribute("newRuleForm", new FormulaRuleForm());
        populate(current, "formula", model);
        return "admin/formula-rules";
    }

    private PayrollConfig buildCandidate(FormulaRuleForm form, PayrollConfig current) {
        List<FormulaRuleConfig> formulaRules = merge(form, current.getFormulaRules());

        List<AttributeConfig> attributes = new ArrayList<>(current.getAttributes());
        if (form.isAddNewAttribute() && !isBlank(form.getNewOutputAttribute())) {
            attributes.add(AttributeConfig.builder()
                    .id(form.getNewOutputAttribute().trim())
                    .displayName(form.getNewOutputAttribute().trim())
                    .dataType(AttributeDataType.NUMBER)
                    .source(AttributeSource.COMPUTED)
                    .defaultValue(NODES.numberNode(0))
                    .build());
        }
        return PayrollConfig.builder()
                .attributes(attributes)
                .eligibilityRules(current.getEligibilityRules())
                .formulaRules(formulaRules)
                .stages(current.getStages())
                .build();
    }

    private List<FormulaRuleConfig> merge(FormulaRuleForm form, List<FormulaRuleConfig> current) {
        List<FormulaRuleConfig> result = new ArrayList<>();
        if (form.isDeleted()) {
            for (FormulaRuleConfig rule : current) {
                if (!rule.getRuleId().equals(form.getOriginalRuleId())) {
                    result.add(rule);
                }
            }
            return result;
        }
        FormulaRuleConfig edited = toConfig(form);
        boolean replaced = false;
        for (FormulaRuleConfig rule : current) {
            if (rule.getRuleId().equals(form.getOriginalRuleId())) {
                result.add(edited);
                replaced = true;
            } else {
                result.add(rule);
            }
        }
        if (!replaced) {
            result.add(edited);
        }
        return result;
    }

    private FormulaRuleConfig toConfig(FormulaRuleForm form) {
        return FormulaRuleConfig.builder()
                .ruleId(trimToNull(form.getRuleId()))
                .name(trimToNull(form.getName()))
                .description(trimToNull(form.getDescription()))
                .priority(form.getPriority() == null ? 0 : form.getPriority())
                .condition(trimToNull(form.getCondition()))
                .formula(trimToNull(form.getFormula()))
                .outputAttribute(trimToNull(form.getOutputAttribute()))
                .componentType(parseComponentType(form.getComponentType()))
                .build();
    }

    private FormulaRuleForm toForm(FormulaRuleConfig rule) {
        FormulaRuleForm form = new FormulaRuleForm();
        form.setOriginalRuleId(rule.getRuleId());
        form.setRuleId(rule.getRuleId());
        form.setName(rule.getName());
        form.setDescription(rule.getDescription());
        form.setPriority(rule.getPriority());
        form.setCondition(rule.getCondition());
        form.setFormula(rule.getFormula());
        form.setOutputAttribute(rule.getOutputAttribute());
        form.setComponentType(rule.getComponentType() == null ? null : rule.getComponentType().name());
        return form;
    }

    private List<FormulaRuleForm> reRenderedForms(FormulaRuleForm submitted, PayrollConfig current) {
        List<FormulaRuleForm> forms = new ArrayList<>();
        for (FormulaRuleConfig rule : current.getFormulaRules()) {
            forms.add(rule.getRuleId().equals(submitted.getOriginalRuleId()) ? submitted : toForm(rule));
        }
        if (submitted.getOriginalRuleId() == null || submitted.getOriginalRuleId().isBlank()) {
            forms.add(submitted);
        }
        return forms;
    }

    private ComponentType parseComponentType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ComponentType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String mvelMessage(RuntimeException e) {
        StringBuilder message = new StringBuilder();
        Throwable cause = e;
        while (cause != null) {
            String text = cause.getMessage();
            if (text != null && !text.isBlank()) {
                message.append(text);
                break;
            }
            cause = cause.getCause();
        }
        return message.isEmpty() ? e.getClass().getSimpleName() : message.toString();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}