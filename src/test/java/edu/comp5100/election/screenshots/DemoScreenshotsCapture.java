package edu.comp5100.election.screenshots;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.config.DemoDataSeeder;
import edu.comp5100.election.system.Browser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.openqa.selenium.By;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Captures the documentation screenshots in docs/screenshots from the running application with a
 * real headless Chrome (`mvnw -Pscreenshots verify`). The application runs with the real demo
 * seed data and the real system clock on an isolated in-memory database, and the browser walks
 * through the demo script. Nothing in these pictures is a mock-up.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"demo", "test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Documentation screenshots from the running demo")
class DemoScreenshotsCapture {

    private static final Path OUTPUT = Path.of("docs", "screenshots");
    private static final String COUNCIL = "Student Council President 2026";
    private static final String REFERENDUM = "Library Hours Referendum";

    @LocalServerPort
    private int port;

    private Browser browser;

    @BeforeAll
    void startBrowser() {
        browser = new Browser(port);
    }

    @AfterAll
    void stopBrowser() {
        if (browser != null) {
            browser.close();
        }
    }

    private void shot(String name) {
        // Shrink first, then make the window as tall as the page, so the picture holds the whole page
        // without blank space left over from a taller page before it.
        browser.resize(1366, 600);
        Number height = (Number) browser.script("return Math.max(document.documentElement.scrollHeight, 600);");
        browser.resize(1366, height.intValue() + 140);
        browser.screenshot(OUTPUT.resolve(name + ".png"));
    }

    private By card(String title) {
        return By.xpath("//li[contains(@class,'election-card')][.//h2[normalize-space()='" + title + "']]");
    }

    private void loginVoter(String username) {
        browser.login(username, DemoDataSeeder.VOTER_PASSWORD);
    }

    private String vote(String candidateName, String shotPrefix) {
        browser.find(card(COUNCIL)).findElement(By.linkText("Vote now")).click();
        browser.chooseCandidate(candidateName);
        if (shotPrefix != null) {
            shot(shotPrefix + "-ballot");
        }
        browser.click(By.id("review-choice"));
        browser.find(By.id("selected-candidate"));
        if (shotPrefix != null) {
            shot(shotPrefix.replace("07", "08") + "-review");
        }
        browser.click(By.id("submit-vote"));
        return browser.text(By.id("receipt-code"));
    }

    @Test
    @DisplayName("Walk through the demo and save one picture per important screen")
    void captureDemoWalkthrough() {
        browser.open("/login");
        browser.find(By.id("username"));
        shot("01-login");

        // Administrator: dashboard, seeded draft, activation, voter accounts.
        browser.login(DemoDataSeeder.ADMIN_USERNAME, DemoDataSeeder.ADMIN_PASSWORD);
        browser.find(By.id("elections-table"));
        shot("02-admin-elections");
        browser.click(By.cssSelector("a[aria-label='Manage " + COUNCIL + "']"));
        browser.find(By.id("activate-election"));
        String managePath = browser.path();
        shot("03-admin-election-draft");
        browser.click(By.id("activate-election"));
        browser.waitUntil(() -> "VOTING".equals(browser.attribute(By.id("election-phase"), "data-phase")));
        shot("04-admin-election-voting-open");
        browser.open("/admin/voters");
        browser.find(By.id("voters-table"));
        shot("05-admin-voters");
        browser.logout();

        // First voter: dashboard, ballot, review, confirmation code, duplicate rejection, verification.
        loginVoter("voter1");
        browser.find(card(COUNCIL));
        shot("06-voter-my-elections");
        String firstCode = vote("Jordan Lee", "07");
        shot("09-vote-confirmation-code");
        String ballotPath = browser.path().replace("/confirmation", "/ballot");
        browser.open(ballotPath);
        browser.find(By.id("rejection-message"));
        shot("10-duplicate-vote-rejected");
        browser.open("/receipt");
        browser.type(By.id("code"), firstCode);
        browser.click(By.id("verify-code"));
        browser.find(By.id("receipt-result"));
        shot("11-receipt-accepted");
        browser.open("/voter");
        browser.logout();

        // Second voter votes for another candidate.
        loginVoter("voter2");
        vote("Priya Nair", null);
        browser.open("/voter");
        browser.logout();

        // Third voter: the deadline fixture rejects a late vote without any administrator action.
        loginVoter("voter3");
        String referendumBallot = browser.find(card(REFERENDUM)).findElement(By.linkText("View results"))
                .getDomAttribute("href").replace("/elections/", "/voter/elections/").replace("/results", "/ballot");
        browser.open(referendumBallot);
        browser.find(By.id("rejection-message"));
        shot("12-late-vote-rejected-deadline");
        browser.open("/voter");
        browser.logout();

        // Fourth voter: not eligible anywhere, and not an administrator.
        loginVoter("voter4");
        browser.find(By.id("no-elections"));
        shot("13-voter-not-eligible");
        browser.open("/admin");
        browser.find(By.id("error-title"));
        shot("14-access-denied");
        browser.open("/voter");
        browser.logout();

        // Disabled account cannot sign in.
        browser.open("/login");
        browser.type(By.id("username"), "voter5");
        browser.type(By.id("password"), DemoDataSeeder.VOTER_PASSWORD);
        browser.click(By.id("login-submit"));
        browser.find(By.id("login-error"));
        shot("15-disabled-account-login-refused");

        // Administrator closes the election; results and audit.
        browser.login(DemoDataSeeder.ADMIN_USERNAME, DemoDataSeeder.ADMIN_PASSWORD);
        browser.open(managePath);
        browser.click(By.id("close-election"));
        browser.waitUntil(() -> "CLOSED".equals(browser.attribute(By.id("election-phase"), "data-phase")));
        shot("16-admin-election-closed");
        browser.click(By.id("view-results"));
        assertThat(browser.text(By.id("total-votes"))).isEqualTo("2");
        shot("17-results-tie");
        browser.open("/admin");
        browser.click(By.cssSelector("a[aria-label='Results of " + REFERENDUM + "']"));
        browser.find(By.id("results-table"));
        shot("18-results-winner-after-deadline");
        browser.open("/admin/audit");
        browser.find(By.id("audit-table"));
        shot("19-admin-audit");
        browser.open("/admin");
        browser.logout();

        // Third voter after the close: the vote is refused; phone-sized pictures.
        loginVoter("voter3");
        browser.open(ballotPath);
        browser.find(By.id("rejection-message"));
        shot("20-vote-after-close-rejected");
        browser.open("/voter");
        browser.find(card(COUNCIL));
        browser.resize(430, 1500);
        browser.screenshot(OUTPUT.resolve("21-mobile-my-elections.png"));
        browser.find(card(COUNCIL)).findElement(By.linkText("View results")).click();
        browser.find(By.id("results-table"));
        browser.screenshot(OUTPUT.resolve("22-mobile-results.png"));

        // Stored evidence reports, when they have been generated before this run.
        browser.resize(1366, 1100);
        captureReport(Path.of("docs", "evidence", "jacoco-html", "index.html"), "23-report-jacoco-coverage");
        captureReport(Path.of("docs", "evidence", "ai-experiment", "pit-expanded-html", "index.html"),
                "24-report-pit-mutation");
        captureReport(Path.of("docs", "evidence", "test-report", "surefire.html"), "25-report-unit-tests");
        captureReport(Path.of("docs", "evidence", "test-report", "failsafe.html"), "26-report-integration-tests");
    }

    private void captureReport(Path report, String name) {
        if (!Files.exists(report)) {
            System.out.println("NOT CAPTURED (report missing): " + report);
            return;
        }
        browser.driver().get(report.toAbsolutePath().toUri().toString());
        browser.find(By.tagName("body"));
        shot(name);
    }
}
