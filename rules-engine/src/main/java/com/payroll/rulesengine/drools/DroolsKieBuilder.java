package com.payroll.rulesengine.drools;

import com.payroll.common.config.EligibilityRuleConfig;
import com.payroll.rulesengine.orchestration.EligibilityEvaluationException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;

/**
 * Compiles a generated DRL document into a Drools {@link KieContainer}.
 *
 * <p>Compilation is expensive, so it must never happen per request. Both
 * {@code ConfigRuntime} (startup and every reload) and the old engine tests go
 * through this single builder; the resulting container is cached inside the
 * {@link com.payroll.rulesengine.config.CompiledConfig} snapshot.
 */
public final class DroolsKieBuilder {

    private DroolsKieBuilder() {
    }

    public static KieContainer compile(List<EligibilityRuleConfig> rules) {
        String drl = new EligibilityRuleCompiler().compile(rules);
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