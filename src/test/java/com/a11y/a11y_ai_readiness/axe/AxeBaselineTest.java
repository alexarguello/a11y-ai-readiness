package com.a11y.a11y_ai_readiness.axe;

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
import java.util.List;

/**
 * Objective, third-party confirmation of the accessible/inaccessible claim:
 * axe-core -- the same engine behind Lighthouse (built into Edge and Chrome DevTools) and the
 * axe DevTools extension -- audits both variants against real WCAG rules. Unlike
 * VisualParityTest this isn't a pass/fail assertion: each page is graded independently and the
 * counts are committed to results/ as evidence, not enforced as a build gate.
 *
 * Audits both the session list AND the registration form per variant: the list page alone
 * (missing landmarks, missing alt text) understates the story, since the more decisive defects --
 * unlabeled form fields -- live on the form, the decisive step of the task. Note this still
 * won't catch everything the NVDA/agent runs catch: axe-core grades whether existing markup's
 * semantics are correct, not whether something interactive-looking was marked up with a role at
 * all -- a bare <div onclick> with zero ARIA attempts often isn't flagged, because it isn't
 * attempting to *be* anything axe can grade. That gap is the point: automated scanning alone
 * under-catches, which is the argument for testing with real screen readers and agents rather
 * than trusting a clean axe report.
 *
 * Loads axe-core from a CDN at test time (the browser fetches it, not this JVM) rather than
 * vendoring it -- unlike the JS actually shipped to /accessible and /inaccessible, this script
 * only needs to run once during prep to generate the baseline, not during the live demo, so a
 * network dependency here is an acceptable tradeoff against committing a ~600KB third-party file.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AxeBaselineTest {

    private static final String AXE_CORE_URL = "https://cdn.jsdelivr.net/npm/axe-core@4.13.0/axe.min.js";

    private record AuditTarget(String variant, String path, String label) {
    }

    private static final List<AuditTarget> TARGETS = List.of(
            new AuditTarget("accessible", "/accessible", "sessions"),
            new AuditTarget("accessible", "/accessible/register/accessible-forms", "register"),
            new AuditTarget("inaccessible", "/inaccessible", "sessions"),
            new AuditTarget("inaccessible", "/inaccessible/register/accessible-forms", "register")
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

    @Test
    void auditBothVariants() throws IOException {
        Path dir = Path.of("results");
        Files.createDirectories(dir);

        try (BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            for (AuditTarget target : TARGETS) {
                String json = audit(page, target);
                Path file = dir.resolve("axe-" + target.variant() + "-" + target.label() + ".json");
                Files.writeString(file, json);
                System.out.println("Wrote " + file.toAbsolutePath() + "\n" + json);
            }
        }
    }

    private String audit(Page page, AuditTarget target) {
        page.navigate("http://localhost:" + port + target.path());
        page.addScriptTag(new Page.AddScriptTagOptions().setUrl(AXE_CORE_URL));

        return (String) page.evaluate("""
                async () => {
                    const result = await axe.run();
                    const violations = result.violations.map(v => ({
                        id: v.id,
                        impact: v.impact,
                        description: v.description,
                        wcagTags: v.tags.filter(t => t.startsWith('wcag')),
                        nodeCount: v.nodes.length
                    }));
                    return JSON.stringify({
                        variant: '%s',
                        page: '%s',
                        url: window.location.href,
                        violationCount: violations.length,
                        totalNodesAffected: violations.reduce((sum, v) => sum + v.nodeCount, 0),
                        violations
                    }, null, 2);
                }
                """.formatted(target.variant(), target.label()));
    }
}
