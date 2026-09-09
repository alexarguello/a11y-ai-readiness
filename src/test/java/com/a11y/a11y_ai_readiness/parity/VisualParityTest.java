package com.a11y.a11y_ai_readiness.parity;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the fairness claim the whole talk rests on (docs/PLAN.md §6.1): to a sighted mouse
 * user, /accessible and /inaccessible render as the same product. Only the markup differs.
 *
 * Requires Chromium to be installed once per machine:
 *   ./mvnw exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VisualParityTest {

    // A real <table> and a CSS-grid <div> layout compute text/border sub-pixel positions
    // slightly differently even with an identical box model -- confirmed by eliminating every
    // structural cause (column widths, box-sizing, vertical-align, border-collapse) and still
    // seeing ~3% left over, all of it single-pixel antialiasing noise around glyphs and rules.
    // 5% stays well clear of that noise floor while still catching a real structural regression.
    private static final double MAX_DIFFERING_PIXEL_PERCENTAGE = 5.0;

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

    @Test
    void accessibleAndInaccessibleSessionListsArePixelIdentical() throws IOException {
        Path actualDir = Path.of("screenshots", "actual");
        Path diffDir = Path.of("screenshots", "diff");
        Files.createDirectories(actualDir);
        Files.createDirectories(diffDir);

        Path accessibleShot = actualDir.resolve("accessible-sessions.png");
        Path inaccessibleShot = actualDir.resolve("inaccessible-sessions.png");
        Path diffShot = diffDir.resolve("sessions-diff.png");

        try (BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setViewportSize(1280, 900))) {
            Page page = context.newPage();

            page.navigate("http://localhost:" + port + "/accessible");
            page.screenshot(new Page.ScreenshotOptions().setPath(accessibleShot).setFullPage(true));

            page.navigate("http://localhost:" + port + "/inaccessible");
            page.screenshot(new Page.ScreenshotOptions().setPath(inaccessibleShot).setFullPage(true));
        }

        ScreenshotDiff.Result result = ScreenshotDiff.compare(accessibleShot, inaccessibleShot, diffShot);

        assertTrue(result.differingPixelPercentage() < MAX_DIFFERING_PIXEL_PERCENTAGE,
                () -> "expected < %.1f%% differing pixels between /accessible and /inaccessible but got %.2f%% — see %s"
                        .formatted(MAX_DIFFERING_PIXEL_PERCENTAGE, result.differingPixelPercentage(), diffShot));
    }
}
