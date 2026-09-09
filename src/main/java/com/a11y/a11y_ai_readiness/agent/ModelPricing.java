package com.a11y.a11y_ai_readiness.agent;

import java.math.BigDecimal;
import java.util.Map;

/**
 * $ per million tokens, checked against the provider's published pricing at the time this was
 * written rather than assumed -- these rates drift, so re-verify them before any cost figure
 * derived from them is published or presented.
 */
final class ModelPricing {

    private record Rate(BigDecimal inputPerMillion, BigDecimal outputPerMillion) {
    }

    private static final Map<String, Rate> RATES = Map.of(
            "grok-4.6", new Rate(new BigDecimal("2.00"), new BigDecimal("6.00")),
            "grok-4.3", new Rate(new BigDecimal("1.25"), new BigDecimal("2.50"))
    );

    private ModelPricing() {
    }

    static BigDecimal estimateCostUsd(String model, long promptTokens, long completionTokens) {
        Rate rate = RATES.get(model);
        if (rate == null) {
            return null;
        }
        BigDecimal inputCost = rate.inputPerMillion()
                .multiply(BigDecimal.valueOf(promptTokens))
                .divide(BigDecimal.valueOf(1_000_000));
        BigDecimal outputCost = rate.outputPerMillion()
                .multiply(BigDecimal.valueOf(completionTokens))
                .divide(BigDecimal.valueOf(1_000_000));
        return inputCost.add(outputCost);
    }
}
