package com.payroll.rulesengine.jeasy;

import com.payroll.common.config.FormulaRuleConfig;
import com.payroll.common.domain.CalculationContext;
import com.payroll.common.domain.ComponentType;
import com.payroll.common.domain.PayComponent;
import com.payroll.rulesengine.orchestration.FormulaEvaluationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import org.jeasy.rules.api.Action;
import org.jeasy.rules.api.Condition;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rule;
import org.jeasy.rules.core.RuleBuilder;
import org.mvel2.MVEL;

/**
 * Converts each {@link FormulaRuleConfig} into a jEasy rule whose condition and
 * action both run MVEL directly against a {@code Map<String,Object>} bridge to
 * the CalculationContext (never reflection on the domain object). The action:
 * <ol>
 *   <li>evaluates the formula against the bridge map,</li>
 *   <li>rounds the result to 2 decimal places (HALF_UP) so monetary outputs are
 *       exact to the cent,</li>
 *   <li>writes it to {@code outputAttribute},</li>
 *   <li>appends a readable line to the calculation trace,</li>
 *   <li>records a {@link PayComponent} when the rule declares a componentType.</li>
 * </ol>
 */
public class FormulaRuleFactory {

    public Rule createRule(FormulaRuleConfig config) {
        return new RuleBuilder()
                .name(config.getRuleId())
                .description(config.getDescription())
                .priority(config.getPriority())
                .when(condition(config))
                .then(action(config))
                .build();
    }

    private Condition condition(FormulaRuleConfig config) {
        return (Facts facts) -> {
            Map<String, Object> bridge = context(facts).getAttributes();
            Object result = MVEL.eval(config.getCondition(), bridge);
            return result instanceof Boolean bool && bool;
        };
    }

    private Action action(FormulaRuleConfig config) {
        return (Facts facts) -> {
            CalculationContext context = context(facts);
            Map<String, Object> bridge = context.getAttributes();

            Object raw = MVEL.eval(config.getFormula(), bridge);
            BigDecimal value = normalize(config, raw);

            bridge.put(config.getOutputAttribute(), value);
            context.addTrace(FormulaTraceBuilder.build(config, bridge, value));

            if (config.getComponentType() != null) {
                context.addPayComponent(PayComponent.builder()
                        .name(config.getName())
                        .type(config.getComponentType())
                        .amount(value)
                        .sourceRule(config.getRuleId())
                        .build());
            }
        };
    }

    private CalculationContext context(Facts facts) {
        CalculationContext context = facts.get("context");
        if (context == null) {
            throw new FormulaEvaluationException("CalculationContext fact 'context' is missing");
        }
        return context;
    }

    private BigDecimal normalize(FormulaRuleConfig config, Object raw) {
        if (raw == null) {
            throw new FormulaEvaluationException(
                    "Formula evaluated to null", config.getRuleId(), config.getOutputAttribute());
        }
        if (!(raw instanceof Number number)) {
            throw new FormulaEvaluationException(
                    "Formula evaluated to non-numeric result: " + raw, config.getRuleId(), config.getOutputAttribute());
        }
        BigDecimal decimal = raw instanceof BigDecimal bigDecimal
                ? bigDecimal
                : new BigDecimal(number.toString());
        return decimal.setScale(2, RoundingMode.HALF_UP);
    }
}