package com.a11y.a11y_ai_readiness.agent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One run's worth of the metrics docs/PLAN.md §7 asks for. Written verbatim to results/*.json
 * (§6.8: numbers are generated, not typed) -- this is what task 9's dashboard reads.
 */
public record AgentRunResult(
        String variant,
        String provider,
        String model,
        Instant startedAt,
        Instant finishedAt,
        boolean success,
        String confirmationCode,
        int toolCalls,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        BigDecimal estimatedCostUsd,
        int finalSnapshotChars,
        String finalModelMessage,
        List<String> actionLog
) {
}
