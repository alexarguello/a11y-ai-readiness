package com.a11y.a11y_ai_readiness.snapshot;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Captures the literal accessibility tree -- via the exact same call,
 * {@code page.locator("body").ariaSnapshot()}, that RegistrationAgentTools uses as the AI agent's
 * only way to perceive a page -- for both variants' session list and registration form, and
 * commits it to results/ as evidence.
 *
 * This isn't a rule check like axe-core: it doesn't grade anything or produce a pass/fail. It's
 * the raw input a screen reader and an AI agent both actually consume, so it's the most direct
 * evidence for the whole project's thesis -- not an assertion that the tree differs between
 * variants, but the literal difference itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccessibilityTreeSnapshotTest {

    private record SnapshotTarget(String variant, String path, String page) {
    }

    private record TreeSnapshot(String variant, String page, String url, String snapshot) {
    }

    private static final List<SnapshotTarget> TARGETS = List.of(
            new SnapshotTarget("accessible", "/accessible", "sessions"),
            new SnapshotTarget("accessible", "/accessible/register/accessible-forms", "register"),
            new SnapshotTarget("inaccessible", "/inaccessible", "sessions"),
            new SnapshotTarget("inaccessible", "/inaccessible/register/accessible-forms", "register")
    );

    @LocalServerPort
    private int port;

    private static Playwright playwright;
    private static Browser browser;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void closeBrowser() {
        browser.close();
        playwright.close();
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void captureBothVariants() throws IOException {
        Path dir = Path.of("results");
        Files.createDirectories(dir);
        var jsonWriter = objectMapper.writerWithDefaultPrettyPrinter();

        try (BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            for (SnapshotTarget target : TARGETS) {
                String url = "http://localhost:" + port + target.path();
                page.navigate(url);
                String snapshot = page.locator("body").ariaSnapshot();

                TreeSnapshot result = new TreeSnapshot(target.variant(), target.page(), url, snapshot);
                Path file = dir.resolve("accessibility-tree-" + target.variant() + "-" + target.page() + ".json");
                jsonWriter.writeValue(file.toFile(), result);
                System.out.println("Wrote " + file.toAbsolutePath());
            }
        }
    }
}
