package edu.comp5100.election.system;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.support.MutableClock;
import edu.comp5100.election.support.ServerTest;
import edu.comp5100.election.support.TestData;
import edu.comp5100.election.support.TestSupportConfig;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * System tests of the voting window in a real browser (FR-07, FR-11, FR-14). The server clock is
 * a controllable test clock, so "too early" and "too late" are reached by moving the clock, not
 * by waiting. No administrator closes the election in these tests and no scheduler runs.
 */
@ServerTest
@DisplayName("System: voting window in a real browser")
class VotingWindowBrowserIT {

    private static final Instant BASE = TestSupportConfig.BASE_TIME;
    private static final Instant START = BASE.plus(Duration.ofHours(1));
    private static final Instant END = BASE.plus(Duration.ofHours(2));

    @LocalServerPort
    private int port;
    @Autowired
    private TestData data;
    @Autowired
    private MutableClock clock;

    private Browser browser;
    private UserAccount voter;
    private UserAccount admin;
    private Election election;

    @BeforeEach
    void openBrowserAndScheduleElection() {
        clock.setInstant(BASE);
        voter = data.voter("window-voter");
        admin = data.admin("window-admin");
        election = data.openElection("Window election", START, END, "Yes", "No");
        data.makeEligible(election, voter);
        browser = new Browser(port);
    }

    @AfterEach
    void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
    }

    private By card() {
        return By.cssSelector("li[data-election-id='" + election.getId() + "']");
    }

    private String ballotPath() {
        return "/voter/elections/" + election.getId() + "/ballot";
    }

    @Test
    @DisplayName("FR-07: before the start there is no vote button and a direct ballot request is refused")
    void votingBeforeTheStartIsRefused() {
        browser.login(voter.getUsername(), TestData.PASSWORD);

        assertThat(browser.text(card())).contains("Not started", "Voting has not started").doesNotContain("Vote now");
        browser.open(ballotPath());
        assertThat(browser.attribute(By.id("rejection-message"), "data-reason")).isEqualTo("VOTING_NOT_STARTED");
        assertThat(browser.text(By.id("rejection-message"))).contains("has not started yet");
    }

    @Test
    @DisplayName("FR-07/FR-14: a vote submitted at the end instant is refused, and results appear without a close")
    void voteAtTheDeadlineIsRefusedAndResultsAppear() {
        browser.login(voter.getUsername(), TestData.PASSWORD);

        clock.setInstant(START);
        browser.open("/voter");
        assertThat(browser.text(card())).contains("Voting open");
        browser.find(card()).findElement(By.linkText("Vote now")).click();
        browser.chooseCandidate("Yes");
        browser.click(By.id("review-choice"));
        assertThat(browser.text(By.id("selected-candidate"))).isEqualTo("Yes");

        // The deadline passes while the voter is looking at the review page.
        clock.setInstant(END);
        browser.click(By.id("submit-vote"));
        assertThat(browser.attribute(By.id("rejection-message"), "data-reason")).isEqualTo("VOTING_ENDED");
        assertThat(browser.text(By.id("rejection-message"))).contains("voting period for this election has ended");

        browser.open("/voter");
        assertThat(browser.text(card())).contains("Voting ended", "You did not vote").doesNotContain("Vote now");
        browser.find(card()).findElement(By.linkText("View results")).click();
        assertThat(browser.text(By.id("total-votes"))).isEqualTo("0");
        assertThat(browser.attribute(By.id("outcome"), "data-outcome")).isEqualTo("NO_VOTES");
        assertThat(browser.text(By.id("outcome"))).contains("No winner is declared");
        browser.open("/voter");
        browser.logout();

        browser.login(admin.getUsername(), TestData.PASSWORD);
        browser.open("/admin/elections/" + election.getId());
        assertThat(browser.attribute(By.id("election-phase"), "data-phase")).isEqualTo("ENDED");
        assertThat(browser.exists(By.id("close-election"))).as("an ended election can still be closed formally")
                .isTrue();
    }

    @Test
    @DisplayName("FR-07/FR-12: a vote submitted just before the deadline is accepted")
    void voteJustBeforeTheDeadlineIsAccepted() {
        browser.login(voter.getUsername(), TestData.PASSWORD);

        clock.setInstant(END.minusSeconds(1));
        browser.open("/voter");
        browser.find(card()).findElement(By.linkText("Vote now")).click();
        browser.chooseCandidate("No");
        browser.click(By.id("review-choice"));
        browser.click(By.id("submit-vote"));

        assertThat(browser.text(By.id("receipt-code"))).hasSize(39);
        clock.setInstant(END);
        browser.open("/elections/" + election.getId() + "/results");
        assertThat(browser.text(By.id("total-votes"))).isEqualTo("1");
        assertThat(browser.text(By.cssSelector("tr[data-candidate='No'] td.votes"))).isEqualTo("1");
    }
}
