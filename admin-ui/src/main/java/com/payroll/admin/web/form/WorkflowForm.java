package com.payroll.admin.web.form;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds the whole workflow.json as one ordered form. Stages are reordered
 * server-side via the moveUp/moveDown action (with the submitted order defining
 * execution order), each stage's ruleIds come from a multi-select, and
 * {@code deleted} drops the stage on Apply/Save.
 */
public class WorkflowForm {

    private List<StageRow> stages = new ArrayList<>();

    public List<StageRow> getStages() {
        return stages;
    }

    public void setStages(List<StageRow> stages) {
        this.stages = stages;
    }

    public static class StageRow {
        private String name;
        private String description;
        private String engine;
        private String execution;
        private List<String> ruleIds = new ArrayList<>();
        private boolean deleted;

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

        public String getEngine() {
            return engine;
        }

        public void setEngine(String engine) {
            this.engine = engine;
        }

        public String getExecution() {
            return execution;
        }

        public void setExecution(String execution) {
            this.execution = execution;
        }

        public List<String> getRuleIds() {
            return ruleIds;
        }

        public void setRuleIds(List<String> ruleIds) {
            this.ruleIds = ruleIds;
        }

        public boolean isDeleted() {
            return deleted;
        }

        public void setDeleted(boolean deleted) {
            this.deleted = deleted;
        }
    }
}