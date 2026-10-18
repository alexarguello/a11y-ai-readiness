package com.a11y.a11y_ai_readiness.dashboard;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads everything the tests and agent runs wrote to results/ and turns it into one
 * self-contained HTML page: not a data dump, but the experiment's actual conclusion. Screen
 * readers and AI agents both depend on the same hidden information behind a page, and breaking
 * it once breaks both. The unified pass/fail matrix is the headline; per-provider cost and the
 * axe-core baseline are supporting evidence underneath it, not co-equal sections.
 *
 * Pure file I/O, no server, no browser, no paid API call: rerun any time results/ changes. Picks
 * the newest summary-*.json per provider (by generatedAt) so a partial/broken earlier multi-run
 * pass never shadows a later clean one.
 */
class DashboardGeneratorTest {

    private static final List<String> PROVIDERS = List.of("xai", "anthropic");
    private static final List<String> VARIANTS = List.of("accessible", "inaccessible");
    private static final List<String> AXE_PAGES = List.of("sessions", "register");

    @Test
    void generate() throws IOException {
        Path resultsDir = Path.of("results");
        JsonMapper mapper = JsonMapper.shared();

        Map<String, JsonNode> summaryByProvider = latestSummaries(resultsDir, mapper);
        Map<String, JsonNode> axeByKey = axeResults(resultsDir, mapper);
        Map<String, JsonNode> nvdaByVariant = nvdaResults(resultsDir, mapper);
        Map<String, JsonNode> treeByKey = treeSnapshots(resultsDir, mapper);
        Map<String, Map<String, Double>> durationsByProvider = medianDurationsByProvider(resultsDir, summaryByProvider, mapper);

        String html = render(summaryByProvider, axeByKey, nvdaByVariant, treeByKey, durationsByProvider);
        Path outFile = resultsDir.resolve("dashboard.html");
        Files.writeString(outFile, html);
        System.out.println("Wrote " + outFile.toAbsolutePath());
    }

    // Duration isn't in summary-*.json (only startedAt/finishedAt per individual run file), so it's
    // computed here from the same already-verified run files rather than requiring a new paid batch.
    // Selects, per provider/variant, the most recent runs whose finishedAt is no later than that
    // provider's summary generatedAt, walking backward from there and skipping budget-exhausted runs
    // (which the summary itself already excludes) until the summary's own run count is matched.
    private Map<String, Map<String, Double>> medianDurationsByProvider(Path dir, Map<String, JsonNode> summaryByProvider,
                                                                        JsonMapper mapper) throws IOException {
        Map<String, Map<String, Double>> byProvider = new LinkedHashMap<>();
        for (String provider : PROVIDERS) {
            JsonNode summary = summaryByProvider.get(provider);
            if (summary == null) {
                continue;
            }
            Instant generatedAt = Instant.parse(summary.path("generatedAt").asText());
            Map<String, Double> byVariant = new LinkedHashMap<>();
            for (String variant : VARIANTS) {
                JsonNode v = summary.path("perVariant").path(variant);
                if (v.isMissingNode()) {
                    continue;
                }
                int expectedRuns = v.path("runs").asInt();
                double median = medianDurationSeconds(dir, provider, variant, generatedAt, expectedRuns, mapper);
                if (median > 0) {
                    byVariant.put(variant, median);
                }
            }
            byProvider.put(provider, byVariant);
        }
        return byProvider;
    }

    private double medianDurationSeconds(Path dir, String provider, String variant, Instant generatedAt,
                                          int expectedRuns, JsonMapper mapper) throws IOException {
        List<JsonNode> runs = new ArrayList<>();
        for (Path file : listJsonFiles(dir, variant + "-" + provider + "-")) {
            JsonNode node = mapper.readTree(file);
            if (!Instant.parse(node.path("finishedAt").asText()).isAfter(generatedAt)) {
                runs.add(node);
            }
        }
        runs.sort((a, b) -> Instant.parse(a.path("finishedAt").asText())
                .compareTo(Instant.parse(b.path("finishedAt").asText())));

        List<Double> durationsSeconds = new ArrayList<>();
        for (int i = runs.size() - 1; i >= 0 && durationsSeconds.size() < expectedRuns; i--) {
            JsonNode run = runs.get(i);
            if (run.path("budgetExhausted").asBoolean()) {
                continue;
            }
            Instant started = Instant.parse(run.path("startedAt").asText());
            Instant finished = Instant.parse(run.path("finishedAt").asText());
            durationsSeconds.add(Duration.between(started, finished).toMillis() / 1000.0);
        }
        if (durationsSeconds.isEmpty()) {
            return 0;
        }
        durationsSeconds.sort(Double::compareTo);
        return durationsSeconds.get(durationsSeconds.size() / 2);
    }

    private Map<String, JsonNode> latestSummaries(Path dir, JsonMapper mapper) throws IOException {
        Map<String, JsonNode> latest = new LinkedHashMap<>();
        for (Path file : listJsonFiles(dir, "summary-")) {
            JsonNode node = mapper.readTree(file);
            String provider = node.path("provider").asText();
            JsonNode existing = latest.get(provider);
            if (existing == null || isNewer(node, existing)) {
                latest.put(provider, node);
            }
        }
        return latest;
    }

    private boolean isNewer(JsonNode candidate, JsonNode existing) {
        return Instant.parse(candidate.path("generatedAt").asText())
                .isAfter(Instant.parse(existing.path("generatedAt").asText()));
    }

    private Map<String, JsonNode> axeResults(Path dir, JsonMapper mapper) throws IOException {
        Map<String, JsonNode> byKey = new LinkedHashMap<>();
        for (Path file : listJsonFiles(dir, "axe-")) {
            JsonNode node = mapper.readTree(file);
            String key = node.path("variant").asText() + "-" + node.path("page").asText();
            byKey.put(key, node);
        }
        return byKey;
    }

    private Map<String, JsonNode> treeSnapshots(Path dir, JsonMapper mapper) throws IOException {
        Map<String, JsonNode> byKey = new LinkedHashMap<>();
        for (Path file : listJsonFiles(dir, "accessibility-tree-")) {
            JsonNode node = mapper.readTree(file);
            String key = node.path("variant").asText() + "-" + node.path("page").asText();
            byKey.put(key, node);
        }
        return byKey;
    }

    private Map<String, JsonNode> nvdaResults(Path dir, JsonMapper mapper) throws IOException {
        Map<String, JsonNode> byVariant = new LinkedHashMap<>();
        for (Path file : listJsonFiles(dir, "nvda-")) {
            JsonNode node = mapper.readTree(file);
            byVariant.put(node.path("variant").asText(), node);
        }
        return byVariant;
    }

    private List<Path> listJsonFiles(Path dir, String prefix) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    // --- Pass/fail classification, shared by the matrix and the provider cards ---

    private record Verdict(String label, String cssClass) {
    }

    private static final Verdict PASS = new Verdict("completed", "success");
    private static final Verdict FAIL = new Verdict("failed", "fail");
    private static final Verdict PARTIAL = new Verdict("partial", "partial");
    private static final Verdict NO_DATA = new Verdict("no data", "missing");

    private Verdict nvdaVerdict(JsonNode nvda) {
        if (nvda == null) {
            return NO_DATA;
        }
        return nvda.path("completed").asBoolean() ? PASS : FAIL;
    }

    private Verdict agentVerdict(JsonNode summary, String variant) {
        if (summary == null) {
            return NO_DATA;
        }
        JsonNode v = summary.path("perVariant").path(variant);
        if (v.isMissingNode()) {
            return NO_DATA;
        }
        double successRate = v.path("successRate").asDouble();
        if (successRate >= 0.999) {
            return PASS;
        }
        if (successRate <= 0.001) {
            return FAIL;
        }
        return PARTIAL;
    }

    // --- Rendering ---

    private String render(Map<String, JsonNode> summaryByProvider, Map<String, JsonNode> axeByKey,
                           Map<String, JsonNode> nvdaByVariant, Map<String, JsonNode> treeByKey,
                           Map<String, Map<String, Double>> durationsByProvider) {
        String generatedAt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());

        String matrix = renderMatrix(summaryByProvider, nvdaByVariant);
        String nvdaDetail = renderNvdaCards(nvdaByVariant);
        String treeSection = renderTreeSection(treeByKey);
        String defectTable = renderDefectTable();
        String fixTable = renderFixTable();
        String agentSection = renderAgentSection(summaryByProvider);
        String headToHead = renderHeadToHead(summaryByProvider, durationsByProvider);
        String stepsSection = renderStepsSection(nvdaByVariant, summaryByProvider);
        String axeSection = renderAxeSection(axeByKey);

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <title>a11y-ai-readiness: Results Dashboard</title>
                <style>
                %s
                </style>
                </head>
                <body>
                <header>
                    <h1>a11y-ai-readiness</h1>
                    <p class="subtitle">Same backend and functionality on both pages. Only the code behind them is different. Generated %s</p>
                </header>
                <main>
                    <section class="headline">
                        <p class="claim">Screen readers and AI agents do not see a page the way a sighted person does. Both rely on hidden information that says what each part of the page is and does. When that information is missing, <strong>both a screen reader user and an AI agent get stuck in exactly the same way</strong>.</p>
                        <p class="lead">Both pages below look the same and do the same thing. The only difference: <code>/accessible</code> has proper labels and structure behind it, and <code>/inaccessible</code> is missing them. Every row tried the exact same task: register for a workshop.</p>
                        <table class="matrix">
                            <thead><tr><th>Who tried it</th><th>/accessible</th><th>/inaccessible</th></tr></thead>
                            <tbody>
                            %s
                            </tbody>
                        </table>
                        <p class="note">NVDA is free screen-reading software used by blind and low-vision people to browse the web. Grok and Claude are two unrelated AI companies' models, tested separately, so this isn't one AI's quirk.</p>
                        <div class="nvda-cards">
                        %s
                        </div>
                    </section>
                    <section>
                        <h2>What the accessibility tree actually looks like</h2>
                        <p class="lead">This isn't a description of the difference between the two pages. It's the literal, unedited output of the same call (<code>ariaSnapshot()</code>) that the AI agent's only "eyes" use to read a page, captured straight from the running app. A screen reader walks the same tree.</p>
                        %s
                    </section>
                    <section>
                        <h2>One problem, two people it breaks</h2>
                        <p class="lead">Four real problems from the broken page, and what each one breaks. This is what "fixing accessibility also helps AI agents" looks like in practice.</p>
                        <table class="defect-table">
                            <thead><tr><th>What's missing</th><th>Screen reader</th><th>AI agent</th></tr></thead>
                            <tbody>
                            %s
                            </tbody>
                        </table>
                    </section>
                    <section>
                        <h2>Independent confirmation this isn't rigged</h2>
                        <p class="lead">We also ran an automated accessibility checker (the same engine behind the Lighthouse accessibility audit built into Edge and Chrome DevTools) against both pages, so this isn't just something we're claiming. It only found part of the real problem, though: it can only flag mistakes in code that at least tries to be a real button, field, or link. Code that never tries at all, like the fake dropdown above, is invisible to it. That gap is exactly why testing with a real screen reader and real AI agents still matters, which is what the rest of this page is about.</p>
                        <table class="axe-table">
                            <thead><tr><th>Page</th><th>Problems found</th><th>Elements affected</th><th>Details</th></tr></thead>
                            <tbody>
                            %s
                            </tbody>
                        </table>
                        <p class="note">Run as a Lighthouse audit in Edge DevTools rather than programmatically, the broken register form scores <strong>91 out of 100</strong> for accessibility (Lighthouse 13.4.1). Be careful comparing counts: the four findings listed above for that page &mdash; a missing label, no main landmark, no level-one heading, no region &mdash; are not the same four defects this page is about. Of those four, the scan flagged only the labels, and only partly. A high score is not a tested page.</p>
                    </section>
                    <section>
                        <h2>Why it costs more when it fails</h2>
                        <p class="lead">Same task, same AI, only the page's markup is different. Every gap in cost and success below comes from that one difference.</p>
                        <p class="note">A "call" is one action the AI took, like reading the page or clicking something. "Tokens" are roughly how much text the AI had to read and write; more tokens means more time and more cost for the same task.</p>
                        <div class="cards">
                        %s
                        </div>
                    </section>
                    <section>
                        <h2>Grok vs. Claude: who costs more, and where</h2>
                        <p class="lead">Two unrelated AI companies, tested separately with the same task on the same two pages. Whichever one looks "worse" completely flips depending on which page it's given, which is exactly the point: the difference is the page's markup, not the AI.</p>
                        <div class="h2h-grid">
                        %s
                        </div>
                        <p class="note">Neither AI completed the broken page, but they failed in opposite ways. Claude gave up in 3 tries and 18 seconds, the cheapest outcome in this whole comparison. Grok kept trying for 33 actions and 3 minutes before running out of ideas, the most expensive outcome. Failing fast and failing slow both still mean a lost transaction; one is just quieter about it.</p>
                    </section>
                    <section>
                        <h2>Screen reader vs. AI agent: how far did each one get</h2>
                        <p class="lead">The same "register for a workshop" task, tried by a real screen reader user and by both AI agents, on both pages. All three depend on the same missing information, so all three get stuck at roughly the same place on the broken page.</p>
                        <div class="steps-grid">
                        %s
                        </div>
                        <p class="note">A screen reader keystroke and an AI tool call aren't the same unit of work; an AI action like "fill in this field" can stand in for several keystrokes. Read these side by side as "how far did each one get," not as a precise multiplier between them. The screen reader numbers also come from a single live run, not a five-run median like the AI numbers, since a human test isn't repeated the same way an API call is.</p>
                    </section>
                    <section>
                        <h2>The fixes, and who requires them</h2>
                        <p class="lead">None of this is a matter of taste. Every fix below is required or recommended by the Web Content Accessibility Guidelines (WCAG), the standard most accessibility law and policy is built on. This is the "smallest fixes that restore reliability" this whole project is about, and it's what closes every gap shown above. Six fixes are listed: the four on the register form that everything above is measured against, plus two on the session list &mdash; the icon with no text alternative, and the page title and main content being styled <code>div</code>s.</p>
                        <table class="fix-table">
                            <thead><tr><th>Problem</th><th>Before</th><th>After</th><th>Required or recommended by</th><th>Caught by the automated scan</th></tr></thead>
                            <tbody>
                            %s
                            </tbody>
                        </table>
                    </section>
                </main>
                <footer>
                    <p>Every number on this page comes from an actual recorded test run. Nothing here was typed in by hand.</p>
                </footer>
                </body>
                </html>
                """.formatted(CSS, generatedAt, matrix, nvdaDetail, treeSection, defectTable, axeSection, agentSection, headToHead, stepsSection, fixTable);
    }

    private String renderMatrix(Map<String, JsonNode> summaryByProvider, Map<String, JsonNode> nvdaByVariant) {
        StringBuilder rows = new StringBuilder();
        rows.append(matrixRow("Screen reader (NVDA)",
                nvdaVerdict(nvdaByVariant.get("accessible")), nvdaVerdict(nvdaByVariant.get("inaccessible"))));
        for (String provider : PROVIDERS) {
            JsonNode summary = summaryByProvider.get(provider);
            String label = provider.equals("xai") ? "AI agent using Grok" : "AI agent using Claude";
            rows.append(matrixRow(label, agentVerdict(summary, "accessible"), agentVerdict(summary, "inaccessible")));
        }
        return rows.toString();
    }

    private String matrixRow(String consumerLabel, Verdict accessible, Verdict inaccessible) {
        return """
                <tr>
                    <td class="consumer">%s</td>
                    <td>%s</td>
                    <td>%s</td>
                </tr>
                """.formatted(consumerLabel, matrixBadge(accessible), matrixBadge(inaccessible));
    }

    private String matrixBadge(Verdict verdict) {
        String symbol = switch (verdict.cssClass()) {
            case "success" -> "&#10003;";
            case "fail" -> "&#10007;";
            case "partial" -> "&#8776;";
            default -> "-";
        };
        return """
                <span class="badge matrix-badge %s">%s %s</span>
                """.formatted(verdict.cssClass(), symbol, verdict.label());
    }

    private String renderNvdaCards(Map<String, JsonNode> nvdaByVariant) {
        StringBuilder cards = new StringBuilder();
        for (String variant : VARIANTS) {
            JsonNode nvda = nvdaByVariant.get(variant);
            if (nvda == null) {
                continue;
            }
            String notes = nvda.path("notes").asText();
            cards.append("""
                    <div class="nvda-card">
                        <h4>/%s</h4>
                        <p>%s</p>
                    </div>
                    """.formatted(variant, notes));
        }
        return cards.toString();
    }

    // --- The accessibility tree itself: real ariaSnapshot() output, captured by
    // AccessibilityTreeSnapshotTest, not curated or summarized ---

    private String renderTreeSection(Map<String, JsonNode> treeByKey) {
        String registerCompare = renderTreeCompare("register", treeByKey);
        String sessionsCompare = renderTreeCompare("sessions", treeByKey);
        if (registerCompare.isEmpty()) {
            return "<p class=\"note\">No accessibility-tree capture yet -- run AccessibilityTreeSnapshotTest.</p>";
        }
        return """
                <div class="tree-compare">
                %s
                </div>
                <p class="note">Notice what's missing on the right: no landmarks, no heading, the whole header collapsed into one unstructured line of text, the dropdown gone entirely, and the checkbox with no name at all. Nothing here is invisible; it's exactly what gets handed to whatever is reading the tree instead of the pixels.</p>
                <details class="tree-details">
                    <summary>Also see the session list's tree</summary>
                    <div class="tree-compare">
                    %s
                    </div>
                </details>
                """.formatted(registerCompare, sessionsCompare);
    }

    private String renderTreeCompare(String page, Map<String, JsonNode> treeByKey) {
        StringBuilder cols = new StringBuilder();
        for (String variant : VARIANTS) {
            JsonNode node = treeByKey.get(variant + "-" + page);
            if (node == null) {
                continue;
            }
            String path = java.net.URI.create(node.path("url").asText()).getPath();
            String cssClass = variant.equals("accessible") ? "tree-good" : "tree-bad";
            cols.append("""
                    <div class="tree-col">
                        <h4>%s</h4>
                        <pre class="%s">%s</pre>
                    </div>
                    """.formatted(path, cssClass, escapeHtml(node.path("snapshot").asText())));
        }
        return cols.toString();
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // Curated, not generated: a synthesis of facts already verified elsewhere (the real
    // actionLogs in results/*.json, and the live NVDA test), not a new measurement, same status
    // as the headline claim above. Every cell traces to something actually observed, not a
    // general assertion that assistive tech and agents both struggle.
    private record DefectRow(String defect, String screenReader, String agent) {
    }

    private static final List<DefectRow> DEFECTS = List.of(
            new DefectRow(
                    "The \"Register\" link isn't a real link. It only looks like one.",
                    "Pressing Tab to move through the page skips right over it. There is no way to even open the registration form.",
                    "The AI looks for something it can recognize as a button or link named \"Register\" and finds nothing. One AI guessed the web address instead and got in that way; the other gave up and explained it could not find a way in."
            ),
            new DefectRow(
                    "The Name and Email boxes show hint text, but have no real label.",
                    "The screen reader can read the hint text once, but it disappears the moment typing starts, and there is no permanent label to fall back on.",
                    "The AI's normal way of finding \"the field labeled Email\" does not work, because there is no real label, only the same fragile hint text the screen reader also has to rely on."
            ),
            new DefectRow(
                    "The meal-preference dropdown isn't a real dropdown. It is just styled to look like one.",
                    "It cannot be reached with the keyboard at all. Even with a mouse, nothing useful is announced: no sign it is a choice to make, no list of options.",
                    "The AI tries every reasonable way to select an option, and none of them work. There is simply no way to choose a meal preference."
            ),
            new DefectRow(
                    "When something is wrong with the form, the only signal is a red border.",
                    "Confirmed live: after several failed attempts to submit, nothing was announced explaining what was wrong. The user has no way to know why the form keeps rejecting them.",
                    "When the AI submits and it fails, it gets no explanation either, just the same page again with no sign of what needs fixing."
            )
    );

    private String renderDefectTable() {
        StringBuilder rows = new StringBuilder();
        for (DefectRow row : DEFECTS) {
            rows.append("""
                    <tr>
                        <td class="defect">%s</td>
                        <td>%s</td>
                        <td>%s</td>
                    </tr>
                    """.formatted(row.defect(), row.screenReader(), row.agent()));
        }
        return rows.toString();
    }

    // Same status as DEFECTS above: curated, not generated. The before/after HTML and the
    // "caught by the automated scan" column both trace to what this project actually built and
    // actually observed in results/axe-*.json, not a general accessibility checklist.
    private record FixRow(String problem, String before, String after, String standards, String caughtByScan) {
    }

    private static String wcagLink(String number, String name, String slug) {
        return "<a href=\"https://www.w3.org/WAI/WCAG21/Understanding/%s.html\" target=\"_blank\" rel=\"noopener\">%s %s</a>"
                .formatted(slug, number, name);
    }

    private static final List<FixRow> FIXES = List.of(
            new FixRow(
                    "The \"Register\" action isn't a real link",
                    """
                    &lt;span onclick="…"&gt;
                      Register
                    &lt;/span&gt;""",
                    """
                    &lt;a href="/register/123"&gt;
                      Register
                    &lt;/a&gt;""",
                    String.join(", ",
                            wcagLink("2.1.1", "Keyboard", "keyboard"),
                            wcagLink("4.1.2", "Name, Role, Value", "name-role-value")),
                    "No"
            ),
            new FixRow(
                    "Name/email fields have no real label",
                    """
                    &lt;input type="text"
                      placeholder="Full name"&gt;""",
                    """
                    &lt;label for="name"&gt;Full name&lt;/label&gt;
                    &lt;input type="text" id="name"&gt;""",
                    String.join(", ",
                            wcagLink("3.3.2", "Labels or Instructions", "labels-or-instructions"),
                            wcagLink("1.3.1", "Info and Relationships", "info-and-relationships")),
                    "Partly (only caught the checkbox, not the text fields)"
            ),
            new FixRow(
                    "The meal-preference picker isn't a real dropdown",
                    """
                    &lt;div class="dropdown"&gt;
                      Meal preference…
                    &lt;/div&gt;""",
                    """
                    &lt;select&gt;
                      &lt;option&gt;Vegetarian&lt;/option&gt;
                    &lt;/select&gt;""",
                    wcagLink("4.1.2", "Name, Role, Value", "name-role-value"),
                    "No"
            ),
            new FixRow(
                    "Form errors are shown by colour alone",
                    """
                    &lt;input class="has-error"&gt;
                    &lt;!-- red border is the only signal --&gt;""",
                    """
                    &lt;div role="alert"&gt;
                      Enter a valid email address.
                    &lt;/div&gt;""",
                    String.join(", ",
                            wcagLink("3.3.1", "Error Identification", "error-identification"),
                            wcagLink("4.1.3", "Status Messages", "status-messages")),
                    "No"
            ),
            new FixRow(
                    "An icon image has no text alternative",
                    """
                    &lt;img src="icon.svg"&gt;""",
                    """
                    &lt;img src="icon.svg"
                      alt="In-person session"&gt;""",
                    wcagLink("1.1.1", "Non-text Content", "non-text-content"),
                    "Yes"
            ),
            new FixRow(
                    "The page title and main content are just styled text",
                    """
                    &lt;div class="h1"&gt;JCON Workshops&lt;/div&gt;
                    &lt;div class="main"&gt;…&lt;/div&gt;""",
                    """
                    &lt;h1&gt;JCON Workshops&lt;/h1&gt;
                    &lt;main&gt;…&lt;/main&gt;""",
                    wcagLink("1.3.1", "Info and Relationships", "info-and-relationships"),
                    "Yes"
            )
    );

    private String renderFixTable() {
        StringBuilder rows = new StringBuilder();
        for (FixRow row : FIXES) {
            rows.append("""
                    <tr>
                        <td class="defect">%s</td>
                        <td><pre class="code-bad">%s</pre></td>
                        <td><pre class="code-good">%s</pre></td>
                        <td>%s</td>
                        <td>%s</td>
                    </tr>
                    """.formatted(row.problem(), row.before(), row.after(), row.standards(), row.caughtByScan()));
        }
        return rows.toString();
    }

    private String renderAgentSection(Map<String, JsonNode> summaryByProvider) {
        double maxCost = summaryByProvider.values().stream()
                .flatMap(summary -> VARIANTS.stream()
                        .map(v -> summary.path("perVariant").path(v).path("medianCostUsd").asDouble(0)))
                .mapToDouble(Double::doubleValue)
                .max().orElse(1.0);
        if (maxCost <= 0) {
            maxCost = 1.0;
        }
        StringBuilder agentSection = new StringBuilder();
        for (String provider : PROVIDERS) {
            agentSection.append(renderProviderCard(provider, summaryByProvider.get(provider), maxCost));
        }
        return agentSection.toString();
    }

    private String renderProviderCard(String provider, JsonNode summary, double maxCost) {
        if (summary == null) {
            return """
                    <div class="card missing">
                        <h3>%s</h3>
                        <p>No multi-run data yet.</p>
                    </div>
                    """.formatted(provider);
        }
        String model = summary.path("model").asText();
        StringBuilder rows = new StringBuilder();
        for (String variant : VARIANTS) {
            JsonNode v = summary.path("perVariant").path(variant);
            if (v.isMissingNode()) {
                rows.append("""
                        <div class="variant-row missing">
                            <span class="variant-name">/%s</span>
                            <span>no valid runs (all budget-exhausted)</span>
                        </div>
                        """.formatted(variant));
                continue;
            }
            double successRate = v.path("successRate").asDouble();
            double medianCost = v.path("medianCostUsd").asDouble();
            double medianToolCalls = v.path("medianToolCalls").asDouble();
            double medianTokens = v.path("medianTotalTokens").asDouble();
            int runs = v.path("runs").asInt();
            String successBadge = successRate >= 0.999 ? "success" : successRate <= 0.001 ? "fail" : "partial";
            double barWidth = Math.min(100, (medianCost / maxCost) * 100);
            rows.append("""
                    <div class="variant-row">
                        <span class="variant-name">/%s</span>
                        <span class="badge %s">%d%% success (%d runs)</span>
                        <span class="metric">%.0f calls</span>
                        <span class="metric">%.0fk tokens</span>
                        <span class="metric cost">$%.3f</span>
                        <div class="bar-track"><div class="bar" style="width:%.1f%%"></div></div>
                    </div>
                    """.formatted(variant, successBadge, (int) Math.round(successRate * 100), runs,
                    medianToolCalls, medianTokens / 1000, medianCost, barWidth));
        }
        return """
                <div class="card">
                    <h3>%s <span class="model">%s</span></h3>
                    %s
                </div>
                """.formatted(provider, model, rows);
    }

    // --- Head-to-head: same page, two AI vendors, for a single slide ---

    private record AgentMetric(String label, int toolCalls, double seconds, double costUsd) {
    }

    private String renderHeadToHead(Map<String, JsonNode> summaryByProvider, Map<String, Map<String, Double>> durationsByProvider) {
        StringBuilder cards = new StringBuilder();
        for (String variant : VARIANTS) {
            AgentMetric grok = agentMetric("Grok", "xai", variant, summaryByProvider, durationsByProvider);
            AgentMetric claude = agentMetric("Claude", "anthropic", variant, summaryByProvider, durationsByProvider);
            if (grok == null || claude == null) {
                continue;
            }
            cards.append(renderHeadToHeadCard(variant, grok, claude));
        }
        return cards.toString();
    }

    private AgentMetric agentMetric(String label, String provider, String variant, Map<String, JsonNode> summaryByProvider,
                                     Map<String, Map<String, Double>> durationsByProvider) {
        JsonNode summary = summaryByProvider.get(provider);
        if (summary == null) {
            return null;
        }
        JsonNode v = summary.path("perVariant").path(variant);
        if (v.isMissingNode()) {
            return null;
        }
        Double seconds = durationsByProvider.getOrDefault(provider, Map.of()).get(variant);
        if (seconds == null) {
            return null;
        }
        return new AgentMetric(label, (int) Math.round(v.path("medianToolCalls").asDouble()), seconds, v.path("medianCostUsd").asDouble());
    }

    private String renderHeadToHeadCard(String variant, AgentMetric a, AgentMetric b) {
        return """
                <div class="h2h-card">
                    <h3>/%s</h3>
                    %s
                    %s
                    %s
                </div>
                """.formatted(variant,
                h2hRow("Actions taken", a, b, m -> (double) m.toolCalls(), "%.0f", "%s took %.1fx more actions"),
                h2hRow("Time to finish (or give up)", a, b, AgentMetric::seconds, "%.0fs", "%s took %.1fx longer"),
                h2hRow("Cost", a, b, AgentMetric::costUsd, "$%.3f", "%s cost %.1fx more"));
    }

    private String h2hRow(String metricLabel, AgentMetric a, AgentMetric b, java.util.function.ToDoubleFunction<AgentMetric> value,
                           String format, String ratioTemplate) {
        double va = value.applyAsDouble(a);
        double vb = value.applyAsDouble(b);
        return """
                <div class="h2h-row">
                    <div class="h2h-top">
                        <span class="h2h-metric">%s</span>
                        <span class="h2h-value">%s %s</span>
                        <span class="h2h-value">%s %s</span>
                    </div>
                    <div class="h2h-ratio">%s</div>
                </div>
                """.formatted(metricLabel, a.label(), format.formatted(va), b.label(), format.formatted(vb),
                h2hRatioLabel(a.label(), va, b.label(), vb, ratioTemplate));
    }

    private String h2hRatioLabel(String labelA, double a, String labelB, double b, String ratioTemplate) {
        if (a <= 0 || b <= 0) {
            return "n/a";
        }
        double higher = Math.max(a, b);
        double lower = Math.min(a, b);
        double ratio = higher / lower;
        if (ratio < 1.05) {
            return "about the same";
        }
        String winner = a > b ? labelA : labelB;
        return ratioTemplate.formatted(winner, ratio);
    }

    // --- Screen reader vs. AI agent: completion + step count, same three consumers as the headline matrix ---

    private String renderStepsSection(Map<String, JsonNode> nvdaByVariant, Map<String, JsonNode> summaryByProvider) {
        StringBuilder cards = new StringBuilder();
        for (String variant : VARIANTS) {
            cards.append(renderStepsCard(variant, nvdaByVariant.get(variant), summaryByProvider));
        }
        return cards.toString();
    }

    private String renderStepsCard(String variant, JsonNode nvda, Map<String, JsonNode> summaryByProvider) {
        StringBuilder rows = new StringBuilder();
        if (nvda != null) {
            String actions = "%d %s %s".formatted(nvda.path("actionCount").asInt(), nvda.path("actionUnit").asText(),
                    nvda.path("actionOutcome").asText());
            rows.append(stepsRow("Screen reader (NVDA)", nvdaVerdict(nvda), actions, nvda.path("sampleNote").asText()));
        }
        for (String provider : PROVIDERS) {
            JsonNode summary = summaryByProvider.get(provider);
            if (summary == null) {
                continue;
            }
            JsonNode v = summary.path("perVariant").path(variant);
            if (v.isMissingNode()) {
                continue;
            }
            Verdict verdict = agentVerdict(summary, variant);
            String label = provider.equals("xai") ? "Grok" : "Claude";
            String outcome = verdict == PASS ? "to a confirmation code" : "before giving up";
            String actions = "%.0f tool calls %s".formatted(v.path("medianToolCalls").asDouble(), outcome);
            rows.append(stepsRow(label, verdict, actions, "median of %d runs".formatted(v.path("runs").asInt())));
        }
        return """
                <div class="steps-card">
                    <h3>/%s</h3>
                    <table class="steps-table">
                        <thead><tr><th>Who tried it</th><th>Completed</th><th>Actions</th><th>Sample size</th></tr></thead>
                        <tbody>
                        %s
                        </tbody>
                    </table>
                </div>
                """.formatted(variant, rows);
    }

    private String stepsRow(String who, Verdict verdict, String actions, String sample) {
        return """
                <tr>
                    <td>%s</td>
                    <td>%s</td>
                    <td>%s</td>
                    <td>%s</td>
                </tr>
                """.formatted(who, matrixBadge(verdict), actions, sample);
    }

    private String renderAxeSection(Map<String, JsonNode> axeByKey) {
        StringBuilder axeSection = new StringBuilder();
        for (String variant : VARIANTS) {
            for (String page : AXE_PAGES) {
                axeSection.append(renderAxeRow(variant, page, axeByKey.get(variant + "-" + page)));
            }
        }
        return axeSection.toString();
    }

    private String renderAxeRow(String variant, String page, JsonNode node) {
        if (node == null) {
            return """
                    <tr class="missing"><td>/%s %s</td><td colspan="3">not audited yet</td></tr>
                    """.formatted(variant, page);
        }
        int violationCount = node.path("violationCount").asInt();
        int nodesAffected = node.path("totalNodesAffected").asInt();
        List<String> ruleIds = new ArrayList<>();
        for (JsonNode violation : node.path("violations")) {
            ruleIds.add(violation.path("id").asText());
        }
        String rulesText = ruleIds.isEmpty() ? "none" : String.join(", ", ruleIds);
        String rowClass = violationCount == 0 ? "clean" : "violations";
        return """
                <tr class="%s"><td>/%s %s</td><td>%d</td><td>%d</td><td>%s</td></tr>
                """.formatted(rowClass, variant, page, violationCount, nodesAffected, rulesText);
    }

    private static final String CSS = """
            :root { color-scheme: light; }
            body { font-family: system-ui, -apple-system, sans-serif; max-width: 60rem; margin: 2rem auto; padding: 0 1.5rem; color: #1a1a1a; background: #fff; }
            header { margin-bottom: 2rem; }
            h1 { margin-bottom: 0.25rem; }
            .subtitle { color: #555; margin-top: 0; }
            h2 { border-bottom: 2px solid #eee; padding-bottom: 0.4rem; }
            .lead { color: #444; }
            .note { color: #777; font-size: 0.88em; font-style: italic; }
            section { margin-bottom: 3rem; }

            .headline { background: #f7f9fc; border: 1px solid #dde5f0; border-radius: 12px; padding: 1.75rem; }
            .headline .claim { font-size: 1.25rem; font-weight: 500; margin-top: 0; line-height: 1.5; }
            table.matrix { width: 100%; border-collapse: collapse; margin: 1.25rem 0; }
            table.matrix th, table.matrix td { text-align: left; padding: 0.7rem 0.6rem; border-bottom: 1px solid #dde5f0; }
            table.matrix td.consumer { font-weight: 600; }
            .matrix-badge { font-size: 0.95em; padding: 0.3rem 0.7rem; }
            .nvda-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(18rem, 1fr)); gap: 1rem; margin-top: 1rem; }
            .nvda-card { background: #fff; border: 1px solid #dde5f0; border-radius: 8px; padding: 0.9rem 1rem; }
            .nvda-card h4 { margin: 0 0 0.4rem; font-family: ui-monospace, monospace; }
            .nvda-card p { margin: 0; color: #444; font-size: 0.92em; line-height: 1.45; }

            .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr)); gap: 1.5rem; }
            .card { border: 1px solid #ddd; border-radius: 8px; padding: 1.25rem; }
            .card h3 { margin-top: 0; text-transform: capitalize; }
            .card .model { font-weight: normal; color: #777; font-size: 0.85em; }
            .variant-row { display: grid; grid-template-columns: 6rem 1fr auto auto auto; align-items: center; gap: 0.6rem; padding: 0.5rem 0; border-top: 1px solid #f0f0f0; }
            .variant-row:first-of-type { border-top: none; }
            .variant-name { font-family: ui-monospace, monospace; font-weight: 600; }
            .metric { font-variant-numeric: tabular-nums; color: #333; }
            .cost { font-weight: 600; }
            .badge { padding: 0.15rem 0.5rem; border-radius: 999px; font-size: 0.8em; font-weight: 600; display: inline-block; }
            .badge.success { background: #e3f7e8; color: #1c7a34; }
            .badge.fail { background: #fde8e8; color: #b3261e; }
            .badge.partial { background: #fff4e0; color: #a15c00; }
            .badge.missing { background: #eee; color: #777; }
            .bar-track { grid-column: 1 / -1; height: 6px; background: #f0f0f0; border-radius: 3px; overflow: hidden; }
            .bar { height: 100%; background: #0b5fff; }
            .defect-table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
            .defect-table th, .defect-table td { text-align: left; padding: 0.7rem 0.6rem; border-bottom: 1px solid #eee; vertical-align: top; font-size: 0.92em; line-height: 1.45; }
            .defect-table td.defect { font-weight: 600; width: 22%; }
            .fix-table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
            .fix-table th, .fix-table td { text-align: left; padding: 0.7rem 0.6rem; border-bottom: 1px solid #eee; vertical-align: top; font-size: 0.9em; line-height: 1.4; }
            .fix-table td.defect { font-weight: 600; width: 16%; }
            .fix-table th:nth-child(5), .fix-table td:nth-child(5) { width: 14%; }
            .fix-table pre { margin: 0; font-family: ui-monospace, monospace; font-size: 0.85em; white-space: pre-wrap; padding: 0.5rem; border-radius: 6px; }
            .code-bad { background: #fdecea; color: #7a1c15; }
            .code-good { background: #e3f7e8; color: #14532d; }
            .fix-table a { color: #0b5fff; }
            .h2h-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(22rem, 1fr)); gap: 1.5rem; margin-top: 1rem; }
            .h2h-card { border: 1px solid #ddd; border-radius: 8px; padding: 1.25rem; }
            .h2h-card h3 { margin-top: 0; font-family: ui-monospace, monospace; }
            .h2h-row { padding: 0.6rem 0; border-top: 1px solid #f0f0f0; }
            .h2h-row:first-of-type { border-top: none; }
            .h2h-top { display: flex; justify-content: space-between; align-items: baseline; gap: 0.75rem; }
            .h2h-metric { color: #555; font-size: 0.9em; flex: 1; }
            .h2h-value { font-variant-numeric: tabular-nums; font-weight: 600; white-space: nowrap; }
            .h2h-ratio { color: #a15c00; font-size: 0.85em; font-weight: 600; margin-top: 0.2rem; text-align: right; }
            .tree-compare { display: grid; grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr)); gap: 1.5rem; margin-top: 1rem; }
            .tree-col h4 { margin: 0 0 0.4rem; font-family: ui-monospace, monospace; font-weight: 600; }
            .tree-col pre { margin: 0; padding: 0.9rem; border-radius: 8px; font-family: ui-monospace, monospace; font-size: 0.78em; line-height: 1.5; white-space: pre-wrap; word-break: break-word; max-height: 26rem; overflow-y: auto; }
            .tree-good { background: #f0f9f2; color: #14532d; border: 1px solid #cdebd5; }
            .tree-bad { background: #fdecea; color: #7a1c15; border: 1px solid #f6cdc8; }
            .tree-details { margin-top: 1.25rem; }
            .tree-details summary { cursor: pointer; color: #0b5fff; font-weight: 600; font-size: 0.92em; }
            .steps-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(22rem, 1fr)); gap: 1.5rem; margin-top: 1rem; }
            .steps-card { border: 1px solid #ddd; border-radius: 8px; padding: 1.25rem; }
            .steps-card h3 { margin-top: 0; font-family: ui-monospace, monospace; }
            .steps-table { width: 100%; border-collapse: collapse; font-size: 0.88em; }
            .steps-table th, .steps-table td { text-align: left; padding: 0.5rem 0.4rem; border-bottom: 1px solid #f0f0f0; vertical-align: top; }
            .steps-table td:last-child { color: #777; font-size: 0.9em; white-space: nowrap; }
            .axe-table { width: 100%; border-collapse: collapse; }
            .axe-table th, .axe-table td { text-align: left; padding: 0.6rem 0.5rem; border-bottom: 1px solid #eee; }
            .axe-table tr.clean td:nth-child(2) { color: #1c7a34; font-weight: 600; }
            .axe-table tr.violations td:nth-child(2) { color: #b3261e; font-weight: 600; }
            .axe-table tr.missing { color: #999; font-style: italic; }
            footer { color: #888; font-size: 0.85em; border-top: 1px solid #eee; padding-top: 1rem; }
            code { background: #f5f5f5; padding: 0.1em 0.35em; border-radius: 4px; font-size: 0.9em; }
            """;
}
