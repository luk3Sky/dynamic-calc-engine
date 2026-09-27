package com.payroll.rulesengine.orchestration;

/**
 * Raised when a jEasy formula rule fails to evaluate or produces an invalid
 * result. Carries the offending ruleId and outputAttribute when known.
 */
public class FormulaEvaluationException extends RuntimeException {

    public FormulaEvaluationException(String message) {
        super(message);
    }

    public FormulaEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }

    public FormulaEvaluationException(String message, String ruleId) {
        super(message + " [ruleId=" + ruleId + "]");
    }

    public FormulaEvaluationException(String message, String ruleId, String outputAttribute) {
        super(message + " [ruleId=" + ruleId + ", outputAttribute=" + outputAttribute + "]");
    }
}