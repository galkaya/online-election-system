package edu.comp5100.election.system;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.policy.ReceiptCodec;
import edu.comp5100.election.support.MutableClock;
import edu.comp5100.election.support.ServerTest;
import edu.comp5100.election.support.TestData;
import edu.comp5100.election.support.TestSupportConfig;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * System test of the main demo scenario in a real headless Chrome against the real embedded
 * server, Spring Security, Thymeleaf, services and an isolated H2 database. Every user signs in
 * through the real login form. The steps run in order and share one election, like the demo.
 *
 * <p>Only the accounts are created directly in the database beforehand; everything else, from
 * creating the election to reading the audit log, happens through the browser.
 */
@ServerTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("System: full election in a real browser")
class ElectionEndToEndIT {

    private static final String TITLE = "Browser Council Election";
    private static final String JORDAN = "Jordan Lee";
    private static final String PRIYA = "Priya Nair";
    private static final String MARCUS = "Marcus Hill";

    @LocalServerPort
    private int port;
    @Autowired
    private TestData data;
    @Autowired
    private MutableClock clock;

    private Browser browser;
    /** A second, independent browser session for the voter who is too late. */
    private Browser lateBrowser;
    private UserAccount admin;
    private UserAccount firstVoter;
    private UserAccount secondVoter;
    private UserAccount lateVoter;
    private UserAccount outsider;
    private String electionId;
    private String firstCode;
    private String secondCode;

    @BeforeAll
    void startBrowsersAndCreateAccounts() {
        clock.setInstant(TestSupportConfig.BASE_TIME);
        admin = data.admin("e2e-admin");
        firstVoter = data.voter("e2e-first");
        secondVoter = data.voter("e2e-second");
        lateVoter = data.voter("e2e-late");
        outsider = data.voter("e2e-outsider");
        browser = new Browser(port);
        lateBrowser = new Browser(port);
    }

    @AfterAll
    void stopBrowsers() {
        if (browser != null) {
            browser.close();
        }
        if (lateBrowser != null) {
            lateBrowser.close();
        }
    }

    private String ballotPath() {
        return "/voter/elections/" + electionId + "/ballot";
    }

    private By electionCard() {
        return By.cssSelector("li[data-election-id='" + electionId + "']");
    }

    private void voteThroughTheBallot(Browser session, String candidateName) {
        session.find(electionCard()).findElement(By.linkText("Vote now")).click();
        session.chooseCandidate(candidateName);
        session.click(By.id("review-choice"));
        assertThat(session.text(By.id("selected-candidate"))).isEqualTo(candidateName);
    }

    @Test
    @Order(1)
    @DisplayName("Step 1 (FR-03, FR-04): the administrator creates an election with three candidates")
    void administratorCreatesElectionWithThreeCandidates() {
        browser.login(admin.getUsername(), TestData.PASSWORD);
        assertThat(browser.path()).isEqualTo("/admin");
        assertThat(browser.attribute(By.tagName("html"), "lang")).isEqualTo("en");

        browser.click(By.id("new-election"));
        browser.type(By.id("title"), TITLE);
        browser.type(By.id("description"), "Created by the browser system test.");
        browser.click(By.id("save-election"));
        browser.waitForText(By.cssSelector(".alert-success"), "draft election was created");
        electionId = browser.path().substring("/admin/elections/".length());
        assertThat(browser.attribute(By.id("election-phase"), "data-phase")).isEqualTo("DRAFT");

        List<String> names = List.of(JORDAN, PRIYA, MARCUS);
        for (int index = 0; index < names.size(); index++) {
            int expectedRows = index + 1;
            browser.type(By.id("candidate-name"), names.get(index));
            browser.type(By.id("candidate-biography"), "Statement of " + names.get(index));
            browser.click(By.id("add-candidate"));
            browser.waitUntil(() -> browser.all(By.cssSelector("#candidates-table tbody tr")).size() == expectedRows);
        }
        assertThat(browser.text(By.id("candidates-table"))).contains(JORDAN, PRIYA, MARCUS);
    }

    @Test
    @Order(2)
    @DisplayName("Step 2 (FR-05, FR-06): the administrator makes three voters eligible and activates the election")
    void administratorGrantsEligibilityAndActivates() {
        for (UserAccount voter : List.of(firstVoter, secondVoter, lateVoter)) {
            By row = By.cssSelector("tr[data-voter='" + voter.getUsername() + "']");
            browser.find(row).findElement(By.tagName("button")).click();
            browser.waitUntil(() -> browser.find(row).findElement(By.cssSelector("td.eligibility")).getText()
                    .contains("✓ Eligible"));
        }
        assertThat(browser.text(By.id("eligible-count"))).isEqualTo("3");

        browser.click(By.id("activate-election"));
        browser.waitUntil(() -> "VOTING".equals(browser.attribute(By.id("election-phase"), "data-phase")));
        assertThat(browser.exists(By.id("add-candidate"))).as("candidates are locked after activation").isFalse();
        browser.logout();
    }

    @Test
    @Order(3)
    @DisplayName("Step 3 (FR-08, FR-09, FR-12): the first voter signs in, votes and receives a confirmation code")
    void firstVoterVotesAndReceivesCode() {
        browser.login(firstVoter.getUsername(), TestData.PASSWORD);
        assertThat(browser.path()).isEqualTo("/voter");
        assertThat(browser.text(electionCard())).contains(TITLE, "Voting open", "You have not voted yet");

        // A second tab of the same session stops on the review page; step 4 submits it as a duplicate.
        browser.openNewTab();
        browser.open("/voter");
        voteThroughTheBallot(browser, MARCUS);
        browser.switchToTab(0);

        voteThroughTheBallot(browser, JORDAN);
        browser.click(By.id("submit-vote"));
        firstCode = browser.text(By.id("receipt-code"));
        assertThat(firstCode).matches("([0-9A-HJKMNP-TV-Z]{4}-){7}[0-9A-HJKMNP-TV-Z]{4}");
        assertThat(browser.path()).as("Post/Redirect/Get: the code is not in the address")
                .isEqualTo("/voter/elections/" + electionId + "/confirmation");

        browser.driver().navigate().refresh();
        assertThat(browser.text(By.id("code-unavailable"))).contains("cannot be displayed again");
        assertThat(browser.pageSource()).doesNotContain(firstCode);
    }

    @Test
    @Order(4)
    @DisplayName("Step 4 (FR-10, FR-11): a second submission by the same voter is rejected")
    void duplicateSubmissionIsRejected() {
        browser.switchToTab(1);
        browser.click(By.id("submit-vote"));
        assertThat(browser.attribute(By.id("rejection-message"), "data-reason")).isEqualTo("ALREADY_VOTED");
        assertThat(browser.text(By.id("rejection-message"))).contains("You have already voted in this election");
        browser.driver().close();
        browser.switchToTab(0);

        browser.open("/voter");
        assertThat(browser.text(electionCard())).contains("You have voted").doesNotContain("Vote now", JORDAN);
        browser.open(ballotPath());
        assertThat(browser.attribute(By.id("rejection-message"), "data-reason")).isEqualTo("ALREADY_VOTED");
        browser.open("/voter");
        browser.logout();
    }

    @Test
    @Order(5)
    @DisplayName("Step 5 (FR-09, FR-12): the second voter votes for a different candidate")
    void secondVoterVotesForAnotherCandidate() {
        browser.login(secondVoter.getUsername(), TestData.PASSWORD);
        voteThroughTheBallot(browser, PRIYA);
        browser.click(By.id("submit-vote"));
        secondCode = browser.text(By.id("receipt-code"));
        assertThat(secondCode).isNotEqualTo(firstCode);
        browser.open("/voter");
        browser.logout();
    }

    @Test
    @Order(6)
    @DisplayName("Step 6 (FR-13): both confirmation codes verify as accepted without revealing voter or candidate")
    void bothCodesVerify() {
        for (String code : List.of(firstCode, secondCode)) {
            browser.open("/receipt");
            browser.type(By.id("code"), code);
            browser.click(By.id("verify-code"));
            assertThat(browser.attribute(By.id("receipt-result"), "data-status")).isEqualTo("ACCEPTED");
            assertThat(browser.path()).as("the code is never part of the address").isEqualTo("/receipt");
            assertThat(browser.pageSource()).doesNotContain(code, JORDAN, PRIYA, firstVoter.getUsername(),
                    secondVoter.getUsername());
        }
        browser.open("/receipt");
        browser.type(By.id("code"), "0000-0000-0000-0000-0000-0000-0000-0000");
        browser.click(By.id("verify-code"));
        assertThat(browser.attribute(By.id("receipt-result"), "data-status")).isEqualTo("NOT_FOUND");
    }

    @Test
    @Order(7)
    @DisplayName("Step 7 (FR-05, FR-16): an account without rights can neither administer nor vote")
    void unauthorisedAccountIsRefused() {
        browser.login(outsider.getUsername(), TestData.PASSWORD);
        assertThat(browser.text(By.id("no-elections"))).contains("No elections available");

        browser.open("/admin");
        assertThat(browser.text(By.id("error-title"))).isEqualTo("Access denied");
        assertThat(browser.text(By.id("error-status"))).isEqualTo("403");
        browser.open("/admin/elections/" + electionId);
        assertThat(browser.text(By.id("error-title"))).isEqualTo("Access denied");

        browser.open(ballotPath());
        assertThat(browser.attribute(By.id("rejection-message"), "data-reason")).isEqualTo("NOT_ELIGIBLE");
        browser.open("/voter");
        browser.logout();
    }

    @Test
    @Order(8)
    @DisplayName("Step 8 (FR-06): the administrator closes the election while a third voter is still on the ballot")
    void administratorClosesElection() {
        // The late voter reaches the review page in an independent browser before the close.
        lateBrowser.login(lateVoter.getUsername(), TestData.PASSWORD);
        voteThroughTheBallot(lateBrowser, MARCUS);

        browser.login(admin.getUsername(), TestData.PASSWORD);
        browser.open("/admin/elections/" + electionId);
        assertThat(browser.text(By.id("votes-cast"))).isEqualTo("2");
        assertThat(browser.exists(By.id("results-table"))).as("no totals while voting is open").isFalse();
        browser.open("/elections/" + electionId + "/results");
        assertThat(browser.text(By.id("results-unavailable"))).contains("Results are published only after");

        browser.open("/admin/elections/" + electionId);
        browser.click(By.id("close-election"));
        browser.waitUntil(() -> "CLOSED".equals(browser.attribute(By.id("election-phase"), "data-phase")));
        assertThat(browser.exists(By.id("close-election"))).isFalse();
    }

    @Test
    @Order(9)
    @DisplayName("Step 9 (FR-06, FR-11): a vote submitted after the close is rejected")
    void voteAfterCloseIsRejected() {
        lateBrowser.click(By.id("submit-vote"));
        assertThat(lateBrowser.attribute(By.id("rejection-message"), "data-reason")).isEqualTo("ELECTION_CLOSED");
        assertThat(lateBrowser.text(By.id("rejection-message"))).contains("closed by the administrator");

        lateBrowser.open("/voter");
        assertThat(lateBrowser.text(electionCard())).contains("Closed", "You did not vote").doesNotContain("Vote now");
    }

    @Test
    @Order(10)
    @DisplayName("Step 10 (FR-14): the tally shows two accepted votes with the expected distribution")
    void tallyMatchesTheVotes() {
        browser.click(By.id("view-results"));
        assertThat(browser.text(By.id("total-votes"))).isEqualTo("2");
        assertThat(browser.text(By.cssSelector("tr[data-candidate='" + JORDAN + "'] td.votes"))).isEqualTo("1");
        assertThat(browser.text(By.cssSelector("tr[data-candidate='" + PRIYA + "'] td.votes"))).isEqualTo("1");
        assertThat(browser.text(By.cssSelector("tr[data-candidate='" + MARCUS + "'] td.votes"))).isEqualTo("0");
        assertThat(browser.attribute(By.id("outcome"), "data-outcome")).isEqualTo("TIE");
        assertThat(browser.text(By.id("outcome"))).contains(JORDAN, PRIYA, "No winner is declared");

        // The late voter, who was eligible, sees the same published result.
        lateBrowser.open("/elections/" + electionId + "/results");
        assertThat(lateBrowser.text(By.id("total-votes"))).isEqualTo("2");
    }

    @Test
    @Order(11)
    @DisplayName("Step 11 (FR-15, NFR-01): the audit page shows events and rejections without choices or codes")
    void auditShowsNoChoicesOrCodes() {
        browser.open("/admin/audit");
        String page = browser.bodyText();
        assertThat(page).contains("Election created", "Election activated", "Election closed",
                "Eligibility granted", "Vote attempt rejected");

        WebElement counts = browser.find(By.cssSelector("#integrity-table tr[data-election-id='" + electionId + "']"));
        assertThat(counts.findElement(By.cssSelector(".participation-count")).getText()).isEqualTo("2");
        assertThat(counts.findElement(By.cssSelector(".ballot-count")).getText()).isEqualTo("2");
        assertThat(counts.findElement(By.cssSelector(".counts-match")).getText()).contains("Yes");

        List<String> rejections = browser.all(By.cssSelector("#audit-table tr[data-event-type='VOTE_REJECTED']"))
                .stream().map(WebElement::getText).toList();
        assertThat(rejections).anyMatch(row -> row.contains(firstVoter.getUsername()) && row.contains("ALREADY_VOTED"));
        assertThat(rejections).anyMatch(row -> row.contains(outsider.getUsername()) && row.contains("NOT_ELIGIBLE"));
        assertThat(rejections).anyMatch(row -> row.contains(lateVoter.getUsername())
                && row.contains("ELECTION_CLOSED"));
        assertThat(rejections).noneMatch(row -> row.contains(JORDAN) || row.contains(PRIYA) || row.contains(MARCUS));

        String source = browser.pageSource();
        for (String code : List.of(firstCode, secondCode)) {
            assertThat(source).doesNotContain(code, ReceiptCodec.normalize(code).orElseThrow());
        }
        // No audit row connects a voter who voted successfully with anything but a rejected attempt.
        assertThat(browser.all(By.cssSelector("#audit-table tbody tr")).stream().map(WebElement::getText))
                .noneMatch(row -> row.contains(secondVoter.getUsername()) && !row.contains("Eligibility"));
        browser.logout();
    }
}
