package com.payroll.common.config;

import com.payroll.common.domain.ComponentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One jEasy-oriented calculation rule from formula-rules.json. The condition
 * and formula are MVEL expressions evaluated against the attribute map.
 * {@code componentType} is present only when the rule produces an itemized pay
 * line; aggregate rules (gross/net/totals) leave it null.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormulaRuleConfig {

    private String ruleId;
    private String name;
    private String description;
    private int priority;
    private String condition;
    private String formula;
    private String outputAttribute;
    private ComponentType componentType;
}