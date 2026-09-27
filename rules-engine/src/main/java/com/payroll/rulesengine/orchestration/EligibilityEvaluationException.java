package com.payroll.rulesengine.orchestration;

/**
 * Raised when Drools eligibility rule compilation or evaluation fails. Carries
 * the failing ruleId (when known) so the cause is immediately debuggable.
 */
public class EligibilityEvaluationException extends RuntimeException {

    public EligibilityEvaluationException(String message) {
        super(message);
    }

    public EligibilityEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }

    public EligibilityEvaluationException(String message, String ruleId) {
        super(message + " [ruleId=" + ruleId + "]");
    }
}