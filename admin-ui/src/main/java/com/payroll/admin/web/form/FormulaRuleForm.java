package com.payroll.admin.web.form;

/**
 * Binds one formula rule's edit form: MVEL condition/formula text areas plus
 * an output attribute dropdown. When {@code addNewAttribute} is ticked, the
 * {@code newOutputAttribute} id is declared as a new COMPUTED NUMBER attribute
 * in attributes.json as part of the same edit.
 */
public class FormulaRuleForm {

    private String originalRuleId;
    private String ruleId;
    private String name;
    private String description;
    private Integer priority;
    private String condition;
    private String formula;
    private String outputAttribute;
    private boolean addNewAttribute;
    private String newOutputAttribute;
    private String componentType;
    private boolean deleted;

    public String getOriginalRuleId() {
        return originalRuleId;
    }

    public void setOriginalRuleId(String originalRuleId) {
        this.originalRuleId = originalRuleId;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getFormula() {
        return formula;
    }

    public void setFormula(String formula) {
        this.formula = formula;
    }

    public String getOutputAttribute() {
        return outputAttribute;
    }

    public void setOutputAttribute(String outputAttribute) {
        this.outputAttribute = outputAttribute;
    }

    public boolean isAddNewAttribute() {
        return addNewAttribute;
    }

    public void setAddNewAttribute(boolean addNewAttribute) {
        this.addNewAttribute = addNewAttribute;
    }

    public String getNewOutputAttribute() {
        return newOutputAttribute;
    }

    public void setNewOutputAttribute(String newOutputAttribute) {
        this.newOutputAttribute = newOutputAttribute;
    }

    public String getComponentType() {
        return componentType;
    }

    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}