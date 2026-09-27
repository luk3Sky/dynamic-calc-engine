package com.payroll.admin.web;

import com.payroll.admin.web.form.AttributeForm;
import com.payroll.admin.web.form.AttributeForm.AttributeRow;
import com.payroll.common.config.AttributeConfig;
import com.payroll.common.config.AttributeDataType;
import com.payroll.common.config.AttributeSource;
import com.payroll.common.config.PayrollConfig;
import java.util.ArrayList;
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
 * Attributes editor: a single table form for all attributes with inline edit /
 * delete, add-new-row (JS), and the shared Validate / Apply to Runtime /
 * Save to File actions. Validation (id required + unique, known dataType,
 * defaultValue typed against dataType) is ConfigValidator — the same logic the
 * engine uses at startup.
 */
@Controller
@RequestMapping("/admin/attributes")
public class AdminAttributesController extends AdminSectionController {

    private static final String PAGE = "/admin/attributes";

    public AdminAttributesController(AdminConfigService service) {
        super(service);
    }

    @GetMapping
    public String editor(Model model) {
        model.addAttribute("form", toForm(service.currentConfig()));
        populate(service.currentConfig(), "attributes", model);
        return "admin/attributes";
    }

    @PostMapping
    public String submit(@ModelAttribute("form") AttributeForm form,
            @RequestParam("action") String action,
            Model model, RedirectAttributes redirect) {
        PayrollConfig candidate = service.withAttributes(toConfig(form));
        String redirectView = resolve(PAGE, candidate, action, model, redirect);
        if (redirectView == null) {
            populate(service.currentConfig(), "attributes", model);
            return "admin/attributes";
        }
        return redirectView;
    }

    private List<AttributeConfig> toConfig(AttributeForm form) {
        List<AttributeConfig> attributes = new ArrayList<>();
        for (AttributeRow row : form.getAttributes()) {
            if (row.isDeleted() || isBlankRow(row)) {
                continue;
            }
            attributes.add(AttributeConfig.builder()
                    .id(trimToNull(row.getId()))
                    .displayName(trimToNull(row.getDisplayName()))
                    .dataType(parseEnum(AttributeDataType.class, row.getDataType()))
                    .source(parseEnum(AttributeSource.class, row.getSource()))
                    .defaultValue(AdminForms.toNode(row.getDefaultValue(),
                            parseEnum(AttributeDataType.class, row.getDataType())))
                    .build());
        }
        return attributes;
    }

    private boolean isBlankRow(AttributeRow row) {
        return isBlank(row.getId()) && isBlank(row.getDisplayName())
                && isBlank(row.getDataType()) && isBlank(row.getSource())
                && isBlank(row.getDefaultValue());
    }

    private AttributeForm toForm(PayrollConfig config) {
        AttributeForm form = new AttributeForm();
        for (AttributeConfig attribute : config.getAttributes()) {
            AttributeRow row = new AttributeRow();
            row.setId(attribute.getId());
            row.setDisplayName(attribute.getDisplayName());
            row.setDataType(attribute.getDataType() == null ? null : attribute.getDataType().name());
            row.setSource(attribute.getSource() == null ? null : attribute.getSource().name());
            row.setDefaultValue(AdminForms.fromNode(attribute.getDefaultValue()));
            form.getAttributes().add(row);
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