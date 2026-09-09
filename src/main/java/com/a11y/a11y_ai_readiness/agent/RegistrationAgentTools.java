package com.a11y.a11y_ai_readiness.agent;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The agent's only way to perceive and act on the page — deliberately mirrors what a real
 * assistive technology or Playwright-MCP-style tool can do: read the accessibility tree, and
 * act on elements by their accessible role/label. Nothing here reads raw HTML or CSS, so
 * /inaccessible breaks these tools for the same underlying reason it breaks a screen reader
 * (docs/PLAN.md §1).
 */
public class RegistrationAgentTools {

    private static final Pattern CONFIRMATION_CODE = Pattern.compile("JCON-[A-Z0-9]{6}");

    // Playwright's locator actions wait for a matching element to *appear* by default (30s) --
    // reasonable for a page that renders asynchronously, wrong here: the page is already fully
    // rendered, so "no match" means "no match", not "not yet". Without a short timeout, every
    // wrong guess against /inaccessible's role-less fake controls burns 30s doing nothing.
    private static final double ACTION_TIMEOUT_MS = 3000;

    private final Page page;
    private final String baseUrl;
    private final List<String> actionLog = new ArrayList<>();
    private int toolCallCount = 0;
    private String confirmationCode = null;
    private int lastSnapshotLength = 0;

    public RegistrationAgentTools(Page page, String baseUrl) {
        this.page = page;
        this.baseUrl = baseUrl;
    }

    @Tool(description = "Navigate to a path on the site under test, e.g. /accessible or /accessible/register/accessible-forms. Returns the resulting page's accessibility tree.")
    public String navigate(@ToolParam(description = "path starting with /") String path) {
        return record("navigate " + path, () -> {
            page.navigate(baseUrl + path, new Page.NavigateOptions().setTimeout(ACTION_TIMEOUT_MS));
            return currentSnapshot();
        });
    }

    @Tool(description = "Get the current page's accessibility tree (roles, accessible names, states). This is your only way to see the page -- call it whenever you're unsure what's on screen.")
    public String snapshot() {
        return record("snapshot", this::currentSnapshot);
    }

    @Tool(description = "Click the element with this accessible role (e.g. link, button, checkbox) and accessible name. Fails if no element exposes that role/name -- that failure is itself meaningful data about the page.")
    public String click(@ToolParam(description = "accessible role, e.g. link, button") String role,
                         @ToolParam(description = "accessible name/label of the element") String name) {
        return record("click role=%s name=%s".formatted(role, name), () -> {
            AriaRole ariaRole = AriaRole.valueOf(role.trim().toUpperCase());
            page.getByRole(ariaRole, new Page.GetByRoleOptions().setName(name))
                    .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(ACTION_TIMEOUT_MS));
            return currentSnapshot();
        });
    }

    @Tool(description = "Type text into the form field with this accessible label. Fails if no field exposes that label.")
    public String fill(@ToolParam(description = "accessible label of the field") String label,
                        @ToolParam(description = "text to type") String value) {
        return record("fill label=%s value=%s".formatted(label, value), () -> {
            page.getByLabel(label).fill(value, new com.microsoft.playwright.Locator.FillOptions().setTimeout(ACTION_TIMEOUT_MS));
            return currentSnapshot();
        });
    }

    @Tool(description = "Choose an option in the select/combobox with this accessible label.")
    public String selectOption(@ToolParam(description = "accessible label of the field") String label,
                                @ToolParam(description = "visible text of the option to choose") String optionText) {
        return record("selectOption label=%s option=%s".formatted(label, optionText), () -> {
            page.getByLabel(label).selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(optionText),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(ACTION_TIMEOUT_MS));
            return currentSnapshot();
        });
    }

    @Tool(description = "Check the checkbox with this accessible label.")
    public String check(@ToolParam(description = "accessible label of the checkbox") String label) {
        return record("check label=%s".formatted(label), () -> {
            page.getByLabel(label).check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(ACTION_TIMEOUT_MS));
            return currentSnapshot();
        });
    }

    int toolCallCount() {
        return toolCallCount;
    }

    List<String> actionLog() {
        return actionLog;
    }

    String confirmationCode() {
        return confirmationCode;
    }

    int lastSnapshotLength() {
        return lastSnapshotLength;
    }

    private String currentSnapshot() {
        String text = page.locator("body").ariaSnapshot();
        lastSnapshotLength = text.length();
        Matcher matcher = CONFIRMATION_CODE.matcher(text);
        if (matcher.find()) {
            confirmationCode = matcher.group();
        }
        return text;
    }

    private interface Action {
        String run() throws Exception;
    }

    private String record(String description, Action action) {
        toolCallCount++;
        try {
            String result = action.run();
            actionLog.add(description + " -> ok");
            return result;
        } catch (Exception e) {
            String error = "TOOL ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            actionLog.add(description + " -> " + error);
            return error + "\n\nCurrent page:\n" + currentSnapshot();
        }
    }
}
