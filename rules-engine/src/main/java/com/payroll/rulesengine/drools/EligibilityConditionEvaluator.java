package com.payroll.rulesengine.drools;

import com.payroll.common.domain.CalculationContext;
import com.payroll.common.config.ConditionConfig;
import com.payroll.common.config.ConditionOperator;
import com.payroll.common.config.EligibilityRuleConfig;
import java.math.BigDecimal;
import java.util.List;

/**
 * Evaluates a single {@code attribute / operator / value} triple against the
 * {@link CalculationContext}. Referenced directly from generated DRL (via
 * {@code eval(...)}), keeping all operator semantics in Java instead of
 * generating ad-hoc comparison code into DRL.
 */
public final class EligibilityConditionEvaluator {

    private EligibilityConditionEvaluator() {
    }

    public static boolean matches(CalculationContext ctx, String attribute, String operator, Object expected) {
        Object actual = ctx.getAttribute(attribute);
        return switch (ConditionOperator.valueOf(operator)) {
            case EQUALS -> equalsSafe(actual, expected);
            case NOT_EQUALS -> !equalsSafe(actual, expected);
            case IN -> actual != null && expected instanceof List<?> list
                    && list.stream().anyMatch(e -> equalsSafe(actual, e));
            // A missing attribute means "no data => not eligible" rather than an
            // error: slab rules only run once grossTotal has been computed.
            case GREATER_THAN -> actual != null && compare(actual, expected) > 0;
            case LESS_THAN -> actual != null && compare(actual, expected) < 0;
            case BETWEEN -> {
                if (actual == null) {
                    yield false;
                }
                List<?> range = (List<?>) expected;
                yield compare(actual, range.get(0)) >= 0 && compare(actual, range.get(1)) <= 0;
            }
        };
    }

    private static boolean equalsSafe(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        if (actual instanceof Number a && expected instanceof Number b) {
            return asBigDecimal(a).compareTo(asBigDecimal(b)) == 0;
        }
        return actual.equals(expected);
    }

    private static int compare(Object actual, Object expected) {
        if (!(actual instanceof Number a && expected instanceof Number b)) {
            throw new IllegalStateException("Cannot numerically compare non-numeric values: " + actual + " vs " + expected);
        }
        return asBigDecimal(a).compareTo(asBigDecimal(b));
    }

    private static BigDecimal asBigDecimal(Number number) {
        if (number instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(number.toString());
    }
}