package com.a11y.a11y_ai_readiness.agent;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Explicitly-triggered "runnable agent": drives one attempt, or a full multi-run pass, at the
 * registration task against the app's own already-started embedded server. Never runs during
 * normal `./mvnw spring-boot:run` or `mvn test` -- only when launched
 * with --agent.run=true, so it never fires (or spends API credit) by accident.
 *
 * Single run:
 *   --agent.run=true --variant=accessible --provider=anthropic
 *
 * Multi-run -- both variants, N runs each, alternating A-B-B-A order so nothing systematically
 * benefits from running first or last:
 *   --agent.run=true --runs=5 --provider=xai
 */
@Component
@ConditionalOnProperty(name = "agent.run", havingValue = "true")
class AgentRunner implements ApplicationRunner {

    // Both providers' ChatModel beans are always constructed (Spring AI doesn't validate a key
    // until it's actually used), but only the one for --provider= actually gets called.
    private static final Map<String, String> DEFAULT_MODEL = Map.of(
            "xai", "grok-4.6",
            "anthropic", "claude-sonnet-5"
    );

    private final Map<String, ChatModel> chatModels;
    private final ObjectWriter jsonWriter;
    private final ConfigurableApplicationContext context;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${agent.headless:true}")
    private boolean headless;

    @Value("${agent.max.turns:40}")
    private int maxToolCalls;

    @Value("${agent.provider:xai}")
    private String defaultProvider;

    AgentRunner(OpenAiChatModel openAiChatModel, AnthropicChatModel anthropicChatModel,
                ObjectMapper objectMapper, ConfigurableApplicationContext context) {
        this.chatModels = Map.of("xai", openAiChatModel, "anthropic", anthropicChatModel);
        this.jsonWriter = objectMapper.writerWithDefaultPrettyPrinter();
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String provider = args.containsOption("provider") ? args.getOptionValues("provider").get(0) : defaultProvider;
        ChatModel chatModel = chatModels.get(provider);
        if (chatModel == null) {
            throw new IllegalArgumentException(
                    "Unknown --provider=" + provider + ", expected one of " + chatModels.keySet());
        }
        String model = args.containsOption("model") ? args.getOptionValues("model").get(0) : DEFAULT_MODEL.get(provider);
        String baseUrl = "http://localhost:" + serverPort;

        // Multi-run is opt-in only via an explicit --runs= argument -- it never triggers from a
        // property default, so a command line that worked before (single run) keeps working
        // exactly as before and can't silently start spending more API credit than intended.
        int runsPerVariant = args.containsOption("runs") ? Integer.parseInt(args.getOptionValues("runs").get(0)) : 1;

        if (runsPerVariant > 1) {
            runMultiple(provider, model, chatModel, baseUrl, runsPerVariant);
        } else {
            String variant = args.containsOption("variant") ? args.getOptionValues("variant").get(0) : "accessible";
            runSingle(variant, provider, model, chatModel, baseUrl);
        }

        System.exit(SpringApplication.exit(context, () -> 0));
    }

    private void runSingle(String variant, String provider, String model, ChatModel chatModel, String baseUrl)
            throws Exception {
        System.out.println("Agent run starting: variant=" + variant + " provider=" + provider
                + " model=" + model + " baseUrl=" + baseUrl);

        AgentRunResult result = SingleAgentRun.execute(variant, provider, model, chatModel, baseUrl, maxToolCalls, headless);

        writeResult(result);
        printResultSummary(result);
    }

    private void runMultiple(String provider, String model, ChatModel chatModel, String baseUrl, int runsPerVariant)
            throws Exception {
        List<String> sequence = alternatingSequence(runsPerVariant);
        System.out.println("Multi-run starting: provider=" + provider + " model=" + model
                + " runsPerVariant=" + runsPerVariant + " totalRuns=" + sequence.size() + " order=" + sequence);

        List<AgentRunResult> results = new ArrayList<>();
        for (int i = 0; i < sequence.size(); i++) {
            String variant = sequence.get(i);
            System.out.println("--- run " + (i + 1) + "/" + sequence.size() + ": variant=" + variant + " ---");
            AgentRunResult result = SingleAgentRun.execute(variant, provider, model, chatModel, baseUrl, maxToolCalls, headless);
            writeResult(result);
            printResultSummary(result);
            results.add(result);
        }

        MultiRunSummary summary = RunStats.summarize(provider, model, results);
        writeSummary(summary);
        printSummary(summary);
    }

    /**
     * A-B-B-A repeating order so nothing systematically benefits from always running first or
     * last; N of each variant across 2N total runs.
     */
    static List<String> alternatingSequence(int runsPerVariant) {
        String[] block = {"accessible", "inaccessible", "inaccessible", "accessible"};
        List<String> sequence = new ArrayList<>();
        int total = runsPerVariant * 2;
        for (int i = 0; i < total; i++) {
            sequence.add(block[i % block.length]);
        }
        return sequence;
    }

    private void printResultSummary(AgentRunResult result) {
        System.out.println("Agent run finished: success=" + result.success() + " toolCalls=" + result.toolCalls()
                + "/" + result.maxToolCalls()
                + " totalTokens=" + result.totalTokens() + " estimatedCostUsd=" + result.estimatedCostUsd());
        if (result.budgetExhausted()) {
            System.out.println("WARNING: hit the " + result.maxToolCalls() + "-call cap, so the model was cut off "
                    + "rather than finishing. Treat this run as spoiled data and exclude it from aggregates.");
        }
    }

    private void printSummary(MultiRunSummary summary) {
        System.out.println("Multi-run finished:");
        summary.perVariant().forEach((variant, stats) ->
                System.out.println("  " + variant + ": " + stats.runs() + " runs (" + stats.excludedBudgetExhausted()
                        + " excluded), successRate=" + stats.successRate()
                        + ", medianToolCalls=" + stats.medianToolCalls()
                        + ", medianTokens=" + stats.medianTotalTokens()
                        + ", medianCostUsd=" + stats.medianCostUsd()
                        + ", totalCostUsd=" + stats.totalCostUsd()));
    }

    private void writeResult(AgentRunResult result) throws Exception {
        Path dir = Path.of("results");
        Files.createDirectories(dir);
        String filename = "%s-%s-%d.json".formatted(result.variant(), result.provider(), result.startedAt().toEpochMilli());
        Path file = dir.resolve(filename);
        jsonWriter.writeValue(file.toFile(), result);
        System.out.println("Wrote " + file.toAbsolutePath());
    }

    private void writeSummary(MultiRunSummary summary) throws Exception {
        Path dir = Path.of("results");
        Files.createDirectories(dir);
        String filename = "summary-%s-%d.json".formatted(summary.provider(), summary.generatedAt().toEpochMilli());
        Path file = dir.resolve(filename);
        jsonWriter.writeValue(file.toFile(), summary);
        System.out.println("Wrote " + file.toAbsolutePath());
    }
}
