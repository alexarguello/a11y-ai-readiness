package com.a11y.a11y_ai_readiness.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregates a batch of AgentRunResults into medians/ranges per variant (docs/PLAN.md §6.6).
 * Runs that hit the tool-call budget are excluded: the model was cut off rather than deciding it
 * was finished, so its numbers describe the cap, not the task -- spoiled data (see
 * RegistrationAgentTools.budgetExhausted()).
 */
final class RunStats {

    private static final List<String> VARIANTS = List.of("accessible", "inaccessible");

    private RunStats() {
    }

    static MultiRunSummary summarize(String provider, String model, List<AgentRunResult> results) {
        Map<String, MultiRunSummary.VariantStats> perVariant = new LinkedHashMap<>();
        for (String variant : VARIANTS) {
            List<AgentRunResult> forVariant = results.stream().filter(r -> r.variant().equals(variant)).toList();
            List<AgentRunResult> valid = forVariant.stream().filter(r -> !r.budgetExhausted()).toList();
            int excluded = forVariant.size() - valid.size();
            if (valid.isEmpty()) {
                continue;
            }
            perVariant.put(variant, variantStats(valid, excluded));
        }
        return new MultiRunSummary(provider, model, Instant.now(), perVariant);
    }

    private static MultiRunSummary.VariantStats variantStats(List<AgentRunResult> valid, int excluded) {
        int runs = valid.size();
        long successes = valid.stream().filter(AgentRunResult::success).count();

        List<Integer> toolCalls = valid.stream().map(AgentRunResult::toolCalls).sorted().toList();
        List<Long> tokens = valid.stream().map(AgentRunResult::totalTokens).sorted().toList();
        List<BigDecimal> costs = valid.stream()
                .map(AgentRunResult::estimatedCostUsd)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        BigDecimal totalCost = costs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        return new MultiRunSummary.VariantStats(
                runs,
                excluded,
                (double) successes / runs,
                medianInt(toolCalls),
                toolCalls.get(0),
                toolCalls.get(toolCalls.size() - 1),
                medianLong(tokens),
                tokens.get(0),
                tokens.get(tokens.size() - 1),
                medianDecimal(costs),
                totalCost
        );
    }

    private static double medianInt(List<Integer> sorted) {
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static double medianLong(List<Long> sorted) {
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static BigDecimal medianDecimal(List<BigDecimal> sorted) {
        if (sorted.isEmpty()) {
            return null;
        }
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
    }
}
