package com.payroll.admin.web.form;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds one eligibility rule's edit form: dynamic repeatable condition rows
 * (attribute / operator / value, with the value shape depending on operator:
 * single value, IN list or BETWEEN min/max) and repeatable derived-fact rows.
 *
 * <p>{@code originalRuleId} is the hidden identity used to merge the edit back
 * into the live rule set (an empty value means "new rule"). {@code deleted}
 * drops the rule when the form is submitted.
 */
public class EligibilityRuleForm {

    private String originalRuleId;
    private String ruleId;
    private String description;
    private List<ConditionRow> conditions = new ArrayList<>();
    private List<FactRow> derivedFacts = new ArrayList<>();
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ConditionRow> getConditions() {
        return conditions;
    }

    public void setConditions(List<ConditionRow> conditions) {
        this.conditions = conditions;
    }

    public List<FactRow> getDerivedFacts() {
        return derivedFacts;
    }

    public void setDerivedFacts(List<FactRow> derivedFacts) {
        this.derivedFacts = derivedFacts;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public static class ConditionRow {
        private String attribute;
        private String operator;
        private String value;
        private List<String> inValues = new ArrayList<>();
        private String betweenMin;
        private String betweenMax;
        private boolean deleted;

        public String getAttribute() {
            return attribute;
        }

        public void setAttribute(String attribute) {
            this.attribute = attribute;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public List<String> getInValues() {
            return inValues;
        }

        public void setInValues(List<String> inValues) {
            this.inValues = inValues;
        }

        public String getBetweenMin() {
            return betweenMin;
        }

        public void setBetweenMin(String betweenMin) {
            this.betweenMin = betweenMin;
        }

        public String getBetweenMax() {
            return betweenMax;
        }

        public void setBetweenMax(String betweenMax) {
            this.betweenMax = betweenMax;
        }

        public boolean isDeleted() {
            return deleted;
        }

        public void setDeleted(boolean deleted) {
            this.deleted = deleted;
        }
    }

    public static class FactRow {
        private String attribute;
        private String value;
        private boolean deleted;

        public String getAttribute() {
            return attribute;
        }

        public void setAttribute(String attribute) {
            this.attribute = attribute;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public boolean isDeleted() {
            return deleted;
        }

        public void setDeleted(boolean deleted) {
            this.deleted = deleted;
        }
    }
}