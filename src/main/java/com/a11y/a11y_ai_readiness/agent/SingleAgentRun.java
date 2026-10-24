package com.a11y.a11y_ai_readiness.agent;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;

import java.time.Instant;
import java.util.List;

/**
 * One agent attempt at the registration task, start to finish, against a fresh browser context.
 * Extracted so the single-run CLI path and the multi-run pass share one implementation
 * rather than drifting apart.
 */
final class SingleAgentRun {

    private SingleAgentRun() {
    }

    static AgentRunResult execute(String variant, String provider, String model, ChatModel chatModel,
                                   String baseUrl, int maxToolCalls, boolean headless) {
        Instant startedAt = Instant.now();

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless))) {
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

            return new AgentRunResult(
                    variant,
                    provider,
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
}
