package com.a11y.a11y_ai_readiness.agent;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
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
import java.time.Instant;
import java.util.List;

/**
 * Explicitly-triggered "runnable agent": drives one attempt at the registration task against
 * one variant, using the app's own already-started embedded server as
 * the target. Never runs during normal `./mvnw spring-boot:run` or `mvn test` -- only when
 * launched with --agent.run=true, so it never fires (or spends API credit) by accident.
 *
 *   ./mvnw spring-boot:run -Dspring-boot.run.arguments="--agent.run=true --variant=accessible"
 */
@Component
@ConditionalOnProperty(name = "agent.run", havingValue = "true")
class AgentRunner implements ApplicationRunner {

    private final ChatModel chatModel;
    private final ObjectWriter jsonWriter;
    private final ConfigurableApplicationContext context;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${agent.headless:true}")
    private boolean headless;

    @Value("${agent.max.turns:30}")
    private int maxToolCalls;

    AgentRunner(ChatModel chatModel, ObjectMapper objectMapper, ConfigurableApplicationContext context) {
        this.chatModel = chatModel;
        this.jsonWriter = objectMapper.writerWithDefaultPrettyPrinter();
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String variant = args.containsOption("variant") ? args.getOptionValues("variant").get(0) : "accessible";
        String model = args.containsOption("model")
                ? args.getOptionValues("model").get(0)
                : System.getProperty("agent.model", "grok-4.6");
        String baseUrl = "http://localhost:" + serverPort;

        System.out.println("Agent run starting: variant=" + variant + " model=" + model + " baseUrl=" + baseUrl);

        Instant startedAt = Instant.now();
        AgentRunResult result;

        try (Playwright playwright = Playwright.create()) {
            try (Browser browser = playwright.chromium()
                    .launch(new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(headless))) {
                Page page = browser.newPage();
                RegistrationAgentTools tools = new RegistrationAgentTools(page, baseUrl, maxToolCalls);

                ChatClient chatClient = ChatClient.builder(chatModel).build();

                ChatClientResponse response = chatClient.prompt()
                        .system("""
                                You are testing a workshop registration website by acting as a real user would.
                                You can ONLY perceive and interact with the page through the tools provided --
                                you have no other access to the page (no raw HTML, no screenshots). Use `snapshot`
                                whenever you're unsure what's currently on screen. Use `click`/`fill`/`selectOption`/
                                `check`/`type` to act on elements by their accessible role and name. If a tool call fails
                                because no element exposes the role/name/label you tried, that failure is itself
                                useful information -- report it rather than guessing wildly forever.
                                """)
                        .user("""
                                1. Navigate to /%s
                                2. Find and register for the workshop titled "Building Accessible Forms in Java"
                                3. Fill in the attendee name as "Jordan Lee"
                                4. First submit with the deliberately malformed email "jordan-not-an-email", to test
                                   the site's error handling
                                5. If the site reports a validation error, correct the email to
                                   "jordan.lee@example.com" and submit again
                                6. Before your final submission, also select any meal preference and accept the
                                   terms and conditions
                                7. Once registered, report the confirmation code you were given. If you cannot
                                   complete registration, report exactly what stopped you.
                                """.formatted(variant))
                        .tools(tools)
                        .call()
                        .chatClientResponse();

                Usage usage = response.chatResponse().getMetadata().getUsage();
                long promptTokens = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                long completionTokens = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
                long totalTokens = usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;

                result = new AgentRunResult(
                        variant,
                        "xai",
                        model,
                        startedAt,
                        Instant.now(),
                        tools.confirmationCode() != null,
                        tools.confirmationCode(),
                        tools.toolCallCount(),
                        tools.maxToolCalls(),
                        tools.budgetExhausted(),
                        promptTokens,
                        completionTokens,
                        totalTokens,
                        ModelPricing.estimateCostUsd(model, promptTokens, completionTokens),
                        tools.lastSnapshotLength(),
                        response.chatResponse().getResult().getOutput().getText(),
                        List.copyOf(tools.actionLog())
                );
            }
        }

        writeResult(result);
        System.out.println("Agent run finished: success=" + result.success() + " toolCalls=" + result.toolCalls()
                + "/" + result.maxToolCalls()
                + " totalTokens=" + result.totalTokens() + " estimatedCostUsd=" + result.estimatedCostUsd());
        if (result.budgetExhausted()) {
            System.out.println("WARNING: hit the " + result.maxToolCalls() + "-call cap, so the model was cut off "
                    + "rather than finishing. Treat this run as spoiled data and exclude it from aggregates.");
        }

        System.exit(SpringApplication.exit(context, () -> 0));
    }

    private void writeResult(AgentRunResult result) throws Exception {
        Path dir = Path.of("results");
        Files.createDirectories(dir);
        String filename = "%s-%s-%d.json".formatted(result.variant(), result.provider(), result.startedAt().toEpochMilli());
        Path file = dir.resolve(filename);
        jsonWriter.writeValue(file.toFile(), result);
        System.out.println("Wrote " + file.toAbsolutePath());
    }
}
