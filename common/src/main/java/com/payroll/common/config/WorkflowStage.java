package com.payroll.common.config;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One ordered stage of workflow.json.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowStage {

    private String name;
    private String description;
    private EngineType engine;
    private List<String> ruleIds;
    private StageExecution execution;
}