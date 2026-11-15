package edu.comp5100.election.system;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.service.ElectionAdminService;
import edu.comp5100.election.support.MutableClock;
import edu.comp5100.election.support.ServerTest;
import edu.comp5100.election.support.TestData;
import edu.comp5100.election.support.TestSupportConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Automated accessibility checks in a real browser (NFR-06): document language, headings, form
 * labels, accessible names, table structure, keyboard operation and visible focus. These are DOM
 * and keyboard checks only. They do not replace a manual review with a screen reader; the manual
 * items are listed, with their real status, in docs/ACCESSIBILITY_CHECKLIST.md.
 */
@ServerTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("System: automated accessibility checks in a real browser")
class AccessibilityChecksIT {

    private static final Instant BASE = TestSupportConfig.BASE_TIME;

    /** Returns a list of human-readable problems found in the current page; empty means none. */
    private static final String STRUCTURE_AUDIT = """
            const problems = [];
            const visible = element => !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
            if (document.documentElement.getAttribute('lang') !== 'en') problems.push('html lang is not "en"');
            if (!document.title || !document.title.endsWith('Online Election System')) problems.push('page title missing');
            if (document.querySelectorAll('h1').length !== 1) problems.push('page must have exactly one h1');
            if (!document.querySelector('main#main')) problems.push('main landmark with id "main" missing');
            if (!document.querySelector('a.skip-link[href="#main"]')) problems.push('skip link missing');
            document.querySelectorAll('input, textarea, select').forEach(field => {
              if (field.type === 'hidden') return;
              const labelled = (field.id && document.querySelector('label[for="' + field.id + '"]'))
                  || field.getAttribute('aria-label');
              if (!labelled) problems.push('form field without label: ' + (field.name || field.id));
            });
            document.querySelectorAll('button, a').forEach(control => {
              if (!visible(control)) return;
              const name = (control.textContent || '').trim() || control.getAttribute('aria-label');
              if (!name) problems.push('control without accessible name: ' + control.outerHTML.slice(0, 80));
            });
            document.querySelectorAll('img').forEach(image => {
              if (!image.hasAttribute('alt')) problems.push('image without alt text');
            });
            document.querySelectorAll('table').forEach(table => {
              if (!table.querySelector('caption')) problems.push('table without caption');
              if (table.querySelectorAll('thead th:not([scope])').length) problems.push('header cell without scope');
            });
            document.querySelectorAll('fieldset').forEach(group => {
              if (!group.querySelector('legend')) problems.push('fieldset without legend');
            });
            const ids = [...document.querySelectorAll('[id]')].map(element => element.id);
            const duplicates = ids.filter((id, index) => ids.indexOf(id) !== index);
            if (duplicates.length) problems.push('duplicate ids: ' + [...new Set(duplicates)].join(', '));
            document.querySelectorAll('[aria-describedby]').forEach(element => {
              element.getAttribute('aria-describedby').split(/\\s+/).forEach(id => {
                if (!document.getElementById(id)) problems.push('aria-describedby points to missing id ' + id);
              });
            });
            return problems;
            """;

    @LocalServerPort
    private int port;
    @Autowired
    private TestData data;
    @Autowired
    private MutableClock clock;
    @Autowired
    private ElectionAdminService electionAdminService;

    private Browser browser;
    private UserAccount admin;
    private UserAccount voter;
    private Election openElection;
    private Election closedElection;

    @BeforeAll
    void startBrowserAndCreateData() {
        clock.setInstant(BASE);
        admin = data.admin("a11y-admin");
        voter = data.voter("a11y-voter");
        openElection = data.openElection("Accessible election", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Jordan Lee", "Priya Nair");
        closedElection = data.openElection("Finished election", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Yes", "No");
        data.makeEligible(openElection, voter);
        data.makeEligible(closedElection, voter);
        electionAdminService.close(admin.getUsername(), closedElection.getId());
        browser = new Browser(port);
    }

    @AfterAll
    void stopBrowser() {
        if (browser != null) {
            browser.close();
        }
    }

    @SuppressWarnings("unchecked")
    private void assertNoStructuralProblems(String path) {
        browser.open(path);
        browser.find(By.tagName("h1"));
        List<String> problems = (List<String>) browser.script(STRUCTURE_AUDIT);
        assertThat(problems).as("accessibility problems on %s", path).isEmpty();
    }

    @Test
    @DisplayName("NFR-06: public and voter pages have language, one h1, labels, names and table structure")
    void publicAndVoterPagesAreStructured() {
        assertNoStructuralProblems("/login");
        assertNoStructuralProblems("/login?error");
        assertNoStructuralProblems("/receipt");

        browser.login(voter.getUsername(), TestData.PASSWORD);
        assertNoStructuralProblems("/voter");
        assertNoStructuralProblems("/voter/elections/" + openElection.getId() + "/ballot");
        assertNoStructuralProblems("/elections/" + closedElection.getId() + "/results");
        assertNoStructuralProblems("/elections/" + openElection.getId() + "/results");
        assertNoStructuralProblems("/voter/elections/" + closedElection.getId() + "/ballot");
        assertNoStructuralProblems("/admin");
        assertNoStructuralProblems("/this/page/does/not/exist");
        browser.open("/voter");
        browser.logout();
    }

    @Test
    @DisplayName("NFR-06: administrator pages have language, one h1, labels, names and table structure")
    void adminPagesAreStructured() {
        browser.login(admin.getUsername(), TestData.PASSWORD);
        assertNoStructuralProblems("/admin");
        assertNoStructuralProblems("/admin/elections/new");
        assertNoStructuralProblems("/admin/elections/" + openElection.getId());
        assertNoStructuralProblems("/admin/voters");
        assertNoStructuralProblems("/admin/audit");

        // The invalid form state must stay accessible: errors are linked to their fields.
        browser.open("/admin/elections/new");
        browser.type(By.id("title"), "");
        browser.type(By.id("startsAt"), "tomorrow");
        browser.click(By.id("save-election"));
        WebElement title = browser.find(By.id("title"));
        assertThat(browser.text(By.id("title-error"))).isEqualTo("Enter a title.");
        assertThat(title.getDomAttribute("aria-invalid")).isEqualTo("true");
        assertThat(title.getDomAttribute("aria-describedby")).contains("title-error");
        assertThat(browser.attribute(By.id("form-errors"), "role")).isEqualTo("alert");
        @SuppressWarnings("unchecked")
        List<String> problems = (List<String>) browser.script(STRUCTURE_AUDIT);
        assertThat(problems).isEmpty();
        browser.open("/admin");
        browser.logout();
    }

    @Test
    @DisplayName("NFR-06: the first Tab stop is the skip link and keyboard focus is clearly visible")
    void keyboardFocusIsVisible() {
        // The receipt page has no autofocus field, so the first Tab press shows the natural first stop.
        browser.open("/receipt");
        browser.find(By.id("code"));
        browser.pressKeys(Keys.TAB);
        WebElement first = browser.focused();
        assertThat(first.getDomAttribute("class")).contains("skip-link");
        assertThat(first.getText()).isEqualTo("Skip to main content");
        assertThat(first.getRect().getX()).as("the focused skip link is moved into view").isGreaterThanOrEqualTo(0);
        assertFocusOutlineIsVisible("skip link");

        // The sign-in page focuses the username field; Tab then walks through the form in order.
        browser.open("/login");
        browser.find(By.id("username"));
        for (String id : List.of("username", "password", "login-submit")) {
            assertThat(browser.focused().getDomAttribute("id")).isEqualTo(id);
            assertFocusOutlineIsVisible("#" + id);
            browser.pressKeys(Keys.TAB);
        }
    }

    private void assertFocusOutlineIsVisible(String description) {
        String outlineStyle = (String) browser.script("return getComputedStyle(document.activeElement).outlineStyle;");
        String outlineWidth = (String) browser.script("return getComputedStyle(document.activeElement).outlineWidth;");
        assertThat(outlineStyle).as("focus outline style of %s", description).isNotEqualTo("none");
        assertThat(Double.parseDouble(outlineWidth.replace("px", ""))).as("focus outline width of %s", description)
                .isGreaterThanOrEqualTo(2.0);
    }

    @Test
    @DisplayName("NFR-06: a voter can sign in, choose a candidate and reach the review page with the keyboard only")
    void ballotWorksWithKeyboardOnly() {
        browser.open("/login");
        browser.find(By.id("username")).sendKeys(voter.getUsername(), Keys.TAB, TestData.PASSWORD, Keys.ENTER);
        browser.waitUntil(() -> browser.path().equals("/voter"));

        browser.open("/voter/elections/" + openElection.getId() + "/ballot");
        WebElement firstRadio = browser.find(By.cssSelector("input[type='radio']"));
        firstRadio.sendKeys(Keys.SPACE);
        assertThat(firstRadio.isSelected()).isTrue();
        firstRadio.sendKeys(Keys.ARROW_DOWN);
        WebElement secondRadio = browser.all(By.cssSelector("input[type='radio']")).get(1);
        assertThat(secondRadio.isSelected()).isTrue();
        secondRadio.sendKeys(Keys.ENTER);

        assertThat(browser.text(By.id("selected-candidate"))).isEqualTo("Priya Nair");
        browser.open("/voter");
        browser.logout();
    }

    @Test
    @DisplayName("NFR-06: status is never shown by colour alone: badges and messages carry text")
    void statusIsNotColourOnly() {
        browser.login(voter.getUsername(), TestData.PASSWORD);
        browser.open("/voter");
        List<String> badges = browser.all(By.cssSelector(".badge")).stream().map(WebElement::getText).toList();
        assertThat(badges).isNotEmpty().allMatch(text -> !text.isBlank());
        assertThat(badges).contains("Voting open", "Closed");

        browser.open("/voter/elections/" + closedElection.getId() + "/ballot");
        assertThat(browser.attribute(By.id("rejection-message"), "role")).isEqualTo("alert");
        assertThat(browser.text(By.id("rejection-message"))).isNotBlank();
        browser.open("/voter");
        browser.logout();
    }
}
