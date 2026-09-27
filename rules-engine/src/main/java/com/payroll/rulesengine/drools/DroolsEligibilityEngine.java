package com.payroll.rulesengine.drools;

import com.payroll.common.domain.CalculationContext;
import com.payroll.common.config.EligibilityRuleConfig;
import com.payroll.rulesengine.orchestration.EligibilityEvaluationException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import org.drools.core.base.RuleNameMatchesAgendaFilter;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

/**
 * Runtime Drools engine for eligibility/classification rules.
 *
 * <p>The generated DRL is compiled once into a cached {@link KieBase} at
 * construction (config-load time), which is the performance requirement: rule
 * compilation is expensive and must never happen per request.
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

    private final KieContainer kieContainer;

    public DroolsEligibilityEngine(List<EligibilityRuleConfig> rules) {
        String drl = new EligibilityRuleCompiler().compile(rules);
        this.kieContainer = buildKieContainer(drl);
    }

    /** Fires all eligibility rules against the context and returns it enriched. */
    public CalculationContext runRules(CalculationContext context) {
        return runRules(context, List.of());
    }

    /** Fires only the given ruleIds (empty means all rules). */
    public CalculationContext runRules(CalculationContext context, Collection<String> ruleIds) {
        KieSession session = kieContainer.newKieSession();
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

    private KieContainer buildKieContainer(String drl) {
        KieServices kieServices = KieServices.get();
        KieFileSystem fileSystem = kieServices.newKieFileSystem()
                .write("src/main/resources/eligibility-generated.drl",
                        kieServices.getResources().newByteArrayResource(drl.getBytes(StandardCharsets.UTF_8)));

        KieBuilder kieBuilder = kieServices.newKieBuilder(fileSystem).buildAll();
        if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
            throw new EligibilityEvaluationException("Failed to compile generated Drools eligibility rules: "
                    + kieBuilder.getResults().getMessages(Message.Level.ERROR));
        }
        KieModule kieModule = kieBuilder.getKieModule();
        return kieServices.newKieContainer(kieModule.getReleaseId());
    }
}