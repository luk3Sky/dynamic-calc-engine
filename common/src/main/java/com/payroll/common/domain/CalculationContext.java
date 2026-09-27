package com.payroll.common.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable bag of facts shared by both rule engines.
 *
 * <p>The attribute map is deliberately a plain {@code Map<String, Object>} of
 * Java values (BigDecimal, String, Boolean, Integer) so that MVEL can read and
 * write attributes directly without reflection on this object. Drools rules
 * drive eligibility via {@link #setAttribute(String, Object)} and jEasy
 * formulas write their outputs into the same map through {@link #getAttributes()}.
 *
 * <p>{@link #getCalculationTrace()} accumulates a human-readable audit trail in
 * execution order, and {@link #getPayComponents()} collects the itemized lines
 * surfaced in {@link PayrollResult}.
 */
public class CalculationContext {

    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private final List<String> calculationTrace = new ArrayList<>();
    private final List<PayComponent> payComponents = new ArrayList<>();

    private String employeeId;
    private PayPeriod period;

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public boolean hasAttribute(String name) {
        return attributes.containsKey(name);
    }

    public CalculationContext setAttribute(String name, Object value) {
        attributes.put(name, value);
        return this;
    }

    public CalculationContext putAllAttributes(Map<String, Object> values) {
        attributes.putAll(values);
        return this;
    }

    /**
     * Backing attribute map. Returning the live map is intentional: the jEasy
     * engine evaluates MVEL formulas against this map, so writes land directly
     * back into the context.
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void addTrace(String line) {
        calculationTrace.add(line);
    }

    public List<String> getCalculationTrace() {
        return calculationTrace;
    }

    public void addPayComponent(PayComponent component) {
        payComponents.add(component);
    }

    public List<PayComponent> getPayComponents() {
        return payComponents;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public CalculationContext setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
        return this;
    }

    public PayPeriod getPeriod() {
        return period;
    }

    public CalculationContext setPeriod(PayPeriod period) {
        this.period = period;
        return this;
    }
}