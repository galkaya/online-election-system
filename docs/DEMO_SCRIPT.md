# Demo script (about 9 minutes)

Everything in this script was exercised by the automated browser test `ElectionEndToEndIT` and
by the screenshot run (`docs/screenshots`). Accounts and passwords are in `README.md`.

## Before the demo (not in front of the audience)

1. Build once while online, so that Maven, the libraries and the Chrome driver are cached:
   `.\mvnw.cmd clean verify` and `.\mvnw.cmd -Psystem verify`.
2. Reset the demo data: `.\scripts\reset-demo.ps1 -ConfirmReset`.
3. Start the application: `.\scripts\run-demo.ps1`, then open <http://localhost:8080>.
4. Open three browser windows: a normal window (administrator), a private window (voters), and a
   terminal in the project folder.

## Part 1: administrator sets up and opens the election (about 2 minutes) — FR-01 to FR-06

1. Sign in as `admin`. Show the "Elections" page: one draft, one election whose deadline has
   passed, one that has not started.
2. Click **New election**. Title "Demo Day Election", keep the suggested start and end times,
   click **Create draft election**.
3. Add three candidates (for example "Alex Kim", "Sam Rivera", "Taylor Brooks").
4. Under "Eligible voters" click **Make eligible** for `voter1`, `voter2` and `voter3`.
   Point out that `voter4` stays "Not eligible" and `voter5` is "Disabled".
5. Click **Activate election**. The badge changes to "Voting open"; the candidate form disappears
   because the setup is now locked.

Shorter alternative: open the seeded draft "Student Council President 2026" (three candidates
and eligible voters are already there) and only click **Activate election**.

## Part 2: two voters vote (about 2 minutes) — FR-08, FR-09, FR-12

1. In the private window sign in as `voter1`. "My elections" shows the election as "Voting open"
   and "You have not voted yet".
2. **Vote now**, choose the first candidate, **Review my choice**, **Submit my vote**.
3. The confirmation code appears. Copy it. Reload the page: the code is gone and cannot be shown
   again.
4. Sign out, sign in as `voter2`, vote for a different candidate, copy the second code, sign out.

## Part 3: check the codes (about 1 minute) — FR-13

1. Click **Verify a code** (works without signing in). Paste the first code: "Accepted".
2. Paste the second code: "Accepted". Change one character: "Not found".
3. Point out that the answer shows no voter, candidate or election, and that the code is not in
   the address bar.

## Part 4: rejected attempts (about 1.5 minutes) — FR-05, FR-10, FR-11, FR-16, FR-17

1. Sign in as `voter1` again. The election shows "You have voted" and has no vote button. Open
   the ballot address directly (`/voter/elections/<id>/ballot`): "Vote not accepted. You have
   already voted in this election."
2. Sign in as `voter4`: "No elections available". Open `/admin`: "Access denied" (error 403).
   Open the ballot address: "You are not on the eligibility list for this election."
3. Try to sign in as `voter5` (disabled): sign-in is refused.
4. Deadline without an administrator: sign in as `voter3` and show "Library Hours Referendum"
   with the badge "Voting ended" and "You did not vote"; there is no vote button, and its ballot
   address answers "The voting period for this election has ended."

## Part 5: close, late vote, tally, audit (about 1.5 minutes) — FR-06, FR-14, FR-15

1. Keep `voter3` signed in on "My elections" in the private window.
2. In the administrator window open the election and click **Close election**. The badge changes
   to "Closed".
3. In the private window (`voter3`) click **Vote now** on the still-open page, or open the ballot
   address: "This election has been closed by the administrator."
4. Administrator: **View results**. Two accepted votes, one for each chosen candidate, zero for
   the third, and the banner "Tie ... No winner is declared."
5. Administrator: **Audit**. Show the event log (created, candidates, eligibility, activated,
   closed, three rejected attempts with account and reason) and the count table: 2 participation
   records, 2 stored ballots, "Counts match: Yes". Point out that no row contains a candidate
   choice or a confirmation code, and that the two accepted votes have no row of their own.

## Part 6: JUnit and evidence (about 1.5 minutes) — NFR-10

1. In the terminal run the unit tests of the voting rules live (a few seconds):

   ```powershell
   .\mvnw.cmd test "-Dtest=VotingPolicyTest,TallyCalculatorTest,ElectionLifecyclePolicyTest"
   ```

2. If time allows, run the browser test and let the audience watch the step names
   (about one minute):

   ```powershell
   .\mvnw.cmd -Psystem verify "-Dit.test=ElectionEndToEndIT"
   ```

3. Show the stored reports instead of waiting for long runs. Each file names the run it came
   from:
   - `docs/TEST_RESULTS.md`: all suites with counts, date and commit.
   - `docs/evidence/jacoco-html/index.html`: coverage.
   - `docs/evidence/ai-experiment/pit-expanded-html/index.html`: mutation report.
   - `docs/evidence/ai-experiment/summary.md`: baseline against expanded suite.

Mutation analysis takes about half a minute and the full default build about two minutes on the
development laptop, so both can also be run live if the schedule allows.

## If something goes wrong

- Port 8080 is busy: stop the other program, or start with
  `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo" "-Dspring-boot.run.arguments=--server.port=8081"`.
- The demo data is in a strange state: stop the application, run
  `.\scripts\reset-demo.ps1 -ConfirmReset`, start again.
- A seeded election date has passed (the seeded draft ends 14 days after seeding): reset as
  above, or edit the draft's end time.
