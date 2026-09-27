package com.payroll.admin.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.payroll.admin.web.form.EligibilityRuleForm;
import com.payroll.admin.web.form.EligibilityRuleForm.ConditionRow;
import com.payroll.admin.web.form.EligibilityRuleForm.FactRow;
import com.payroll.common.config.AttributeConfig;
import com.payroll.common.config.AttributeDataType;
import com.payroll.common.config.ConditionConfig;
import com.payroll.common.config.ConditionOperator;
import com.payroll.common.config.EligibilityRuleConfig;
import com.payroll.common.config.PayrollConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Eligibility rules editor (Drools-backed). Each rule is listed collapsed with
 * expand-to-edit; the edit form has dynamic repeatable condition rows whose
 * value input adapts to the operator (single / IN / BETWEEN) and the attribute
 * dataType, plus repeatable derived-fact rows. Attribute dropdowns always
 * reflect the current in-memory attributes so multi-config edits stay
 * consistent within a session.
 */
@Controller
@RequestMapping("/admin/eligibility-rules")
public class AdminEligibilityRulesController extends AdminSectionController {

    private static final String PAGE = "/admin/eligibility-rules";
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    public AdminEligibilityRulesController(AdminConfigService service) {
        super(service);
    }

    @GetMapping
    public String editor(Model model) {
        PayrollConfig config = service.currentConfig();
        List<EligibilityRuleForm> forms = config.getEligibilityRules().stream()
                .map(this::toForm)
                .toList();
        model.addAttribute("ruleForms", new ArrayList<>(forms));
        model.addAttribute("newRuleForm", blankForm());
        populate(config, "eligibility", model);
        return "admin/eligibility-rules";
    }

    @PostMapping
    public String submit(@ModelAttribute("ruleForm") EligibilityRuleForm form,
            @RequestParam("action") String action,
            Model model, RedirectAttributes redirect) {
        PayrollConfig current = service.currentConfig();
        PayrollConfig candidate = service.withEligibilityRules(merge(form, current.getEligibilityRules(), current));

        String redirectView = resolve(PAGE, candidate, action, model, redirect);
        if (redirectView == null) {
            List<EligibilityRuleForm> forms = new ArrayList<>();
            for (EligibilityRuleConfig rule : current.getEligibilityRules()) {
                forms.add(rule.getRuleId().equals(form.getOriginalRuleId()) ? form : toForm(rule));
            }
            if (form.getOriginalRuleId() == null || form.getOriginalRuleId().isBlank()) {
                forms.add(form);
            }
            model.addAttribute("ruleForms", forms);
            model.addAttribute("newRuleForm", blankForm());
            populate(current, "eligibility", model);
            return "admin/eligibility-rules";
        }
        return redirectView;
    }

    private EligibilityRuleForm blankForm() {
        EligibilityRuleForm form = new EligibilityRuleForm();
        form.getConditions().add(new ConditionRow());
        form.getDerivedFacts().add(new FactRow());
        return form;
    }

    private List<EligibilityRuleConfig> merge(EligibilityRuleForm form,
            List<EligibilityRuleConfig> current, PayrollConfig config) {
        List<EligibilityRuleConfig> result = new ArrayList<>();
        if (form.isDeleted()) {
            for (EligibilityRuleConfig rule : current) {
                if (!rule.getRuleId().equals(form.getOriginalRuleId())) {
                    result.add(rule);
                }
            }
            return result;
        }
        EligibilityRuleConfig edited = toConfig(form, config);
        boolean replaced = false;
        for (EligibilityRuleConfig rule : current) {
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

    private EligibilityRuleConfig toConfig(EligibilityRuleForm form, PayrollConfig config) {
        Map<String, AttributeDataType> dataTypes = dataTypes(config);
        List<ConditionConfig> conditions = new ArrayList<>();
        for (ConditionRow row : form.getConditions()) {
            if (row == null || row.isDeleted() || isBlank(row.getAttribute())) {
                continue;
            }
            ConditionOperator operator = parseOperator(row.getOperator());
            AttributeDataType dataType = dataTypes.get(row.getAttribute());
            conditions.add(ConditionConfig.builder()
                    .attribute(trimToNull(row.getAttribute()))
                    .operator(operator)
                    .value(valueFor(row, dataType))
                    .build());
        }
        Map<String, JsonNode> derivedFacts = new HashMap<>();
        for (FactRow row : form.getDerivedFacts()) {
            if (row == null || row.isDeleted() || isBlank(row.getAttribute())) {
                continue;
            }
            derivedFacts.put(trimToNull(row.getAttribute()),
                    AdminForms.toNode(row.getValue(), dataTypes.get(row.getAttribute())));
        }
        return EligibilityRuleConfig.builder()
                .ruleId(trimToNull(form.getRuleId()))
                .description(trimToNull(form.getDescription()))
                .conditions(conditions)
                .derivedFacts(derivedFacts)
                .build();
    }

    private JsonNode valueFor(ConditionRow row, AttributeDataType dataType) {
        ConditionOperator operator = parseOperator(row.getOperator());
        if (operator == ConditionOperator.BETWEEN) {
            if (isBlank(row.getBetweenMin()) || isBlank(row.getBetweenMax())) {
                return NODES.nullNode();
            }
            return NODES.arrayNode()
                    .add(AdminForms.toNode(row.getBetweenMin(), dataType))
                    .add(AdminForms.toNode(row.getBetweenMax(), dataType));
        }
        if (operator == ConditionOperator.IN) {
            return AdminForms.toArrayNode(row.getInValues(), dataType);
        }
        return AdminForms.toNode(row.getValue(), dataType);
    }

    private EligibilityRuleForm toForm(EligibilityRuleConfig rule) {
        EligibilityRuleForm form = new EligibilityRuleForm();
        form.setOriginalRuleId(rule.getRuleId());
        form.setRuleId(rule.getRuleId());
        form.setDescription(rule.getDescription());
        if (rule.getConditions() != null) {
            for (ConditionConfig condition : rule.getConditions()) {
                ConditionRow row = new ConditionRow();
                row.setAttribute(condition.getAttribute());
                row.setOperator(condition.getOperator() == null ? null : condition.getOperator().name());
                if (condition.getValue() != null && condition.getValue().isArray()) {
                    if (condition.getOperator() == ConditionOperator.IN) {
                        List<String> inValues = new ArrayList<>();
                        condition.getValue().forEach(node -> inValues.add(AdminForms.fromNode(node)));
                        row.setInValues(inValues);
                    } else if (condition.getOperator() == ConditionOperator.BETWEEN
                            && condition.getValue().size() == 2) {
                        row.setBetweenMin(AdminForms.fromNode(condition.getValue().get(0)));
                        row.setBetweenMax(AdminForms.fromNode(condition.getValue().get(1)));
                    }
                } else {
                    row.setValue(AdminForms.fromNode(condition.getValue()));
                }
                form.getConditions().add(row);
            }
        }
        if (rule.getDerivedFacts() != null) {
            rule.getDerivedFacts().forEach((attribute, node) -> {
                FactRow row = new FactRow();
                row.setAttribute(attribute);
                row.setValue(AdminForms.fromNode(node));
                form.getDerivedFacts().add(row);
            });
        }
        return form;
    }

    private Map<String, AttributeDataType> dataTypes(PayrollConfig config) {
        Map<String, AttributeDataType> map = new HashMap<>();
        for (AttributeConfig attribute : config.getAttributes()) {
            map.put(attribute.getId(), attribute.getDataType());
        }
        return map;
    }

    private ConditionOperator parseOperator(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ConditionOperator.valueOf(value);
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