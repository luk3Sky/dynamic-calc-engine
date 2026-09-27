package com.payroll.rulesengine.drools;

import com.payroll.common.domain.CalculationContext;
import com.payroll.rulesengine.config.ConfigRuntime;
import com.payroll.rulesengine.orchestration.EligibilityEvaluationException;
import java.util.Collection;
import java.util.List;
import org.drools.core.base.RuleNameMatchesAgendaFilter;
import org.kie.api.runtime.KieSession;

/**
 * Runtime Drools engine for eligibility/classification rules.
 *
 * <p>The compiled {@link org.kie.api.runtime.KieContainer} is not owned by this
 * engine: it lives inside the active {@link ConfigRuntime} generation and is
 * re-read on every invocation. When the admin console calls
 * {@link ConfigRuntime#reload} a fresh KieContainer is compiled and swapped in,
 * so the next request here already evaluates the new rules.
 *
 * <p>A fresh {@link KieSession} is created per calculation request rather than
 * pooled: a KieSession holds mutable working-memory state and is not
 * thread-safe, so a shared/pooled session would leak state across requests and
 * require synchronization. Sessions are cheap after compilation and are
 * disposed deterministically in a finally block.
 *
 * <p>When a workflow stage only needs a subset of rules (e.g. the second
 * lightweight TAX_SLAB pass), the session fires through a
 * {@link RuleNameMatchesAgendaFilter} so only the requested ruleIds run.
 */
public class DroolsEligibilityEngine {

    private final ConfigRuntime runtime;

    public DroolsEligibilityEngine(ConfigRuntime runtime) {
        this.runtime = runtime;
    }

    /** Fires all eligibility rules against the context and returns it enriched. */
    public CalculationContext runRules(CalculationContext context) {
        return runRules(context, List.of());
    }

    /** Fires only the given ruleIds (empty means all rules). */
    public CalculationContext runRules(CalculationContext context, Collection<String> ruleIds) {
        KieSession session = runtime.get().getKieContainer().newKieSession();
        try {
            session.insert(context);
            if (ruleIds == null || ruleIds.isEmpty()) {
                session.fireAllRules();
            } else {
                String regex = "^(" + String.join("|", ruleIds) + ")$";
                session.fireAllRules(new RuleNameMatchesAgendaFilter(regex));
            }
        } catch (RuntimeException e) {
            throw new EligibilityEvaluationException(
                    "Drools eligibility evaluation failed for rules " + ruleIds, e);
        } finally {
            session.dispose();
        }
        return context;
    }
}