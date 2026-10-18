package com.a11y.a11y_ai_readiness.agent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Per-variant medians and spread across a multi-run pass (docs/PLAN.md §6.6) -- never a single
 * lucky run. Written to results/summary-*.json alongside the individual run files it summarizes.
 */
record MultiRunSummary(
        String provider,
        String model,
        Instant generatedAt,
        Map<String, VariantStats> perVariant
) {
    record VariantStats(
            int runs,
            int excludedBudgetExhausted,
            double successRate,
            double medianToolCalls,
            int minToolCalls,
            int maxToolCalls,
            double medianTotalTokens,
            long minTotalTokens,
            long maxTotalTokens,
            BigDecimal medianCostUsd,
            BigDecimal totalCostUsd
    ) {
    }
}
