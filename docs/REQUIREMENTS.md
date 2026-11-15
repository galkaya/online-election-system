# Requirements

Online Election System, COMP 5100 Software Engineering semester project
(Gokdeniz Alkaya and Derrick Martin).

The system is a **simulated election with fictional users**. It is not a certified system for
real public elections and it does not provide end-to-end cryptographic verification.

Sources: the course project description (`ProjectDescription1.pdf`) and the accepted first report
(October 29, 2026). Every requirement below has an identifier that is used in code comments, test
names, `docs/TRACEABILITY.md` and `docs/DEMO_SCRIPT.md`.

## 1. Actors

| Actor | Description |
|---|---|
| Voter | A fictional person with a voter account. Can vote once in each election for which the account is eligible, and can check a confirmation code. |
| Administrator | Configures elections, candidates and voter accounts, opens and closes elections, and reads results and the audit page. Cannot vote. |
| Anonymous visitor | Anyone without a session. Can open the sign-in page and verify a confirmation code. |
| Server clock | Not a person: the single source of time that decides whether a vote is inside the voting window. |

## 2. Functional requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| FR-01 | Session-based sign-in and sign-out with the roles ADMIN and VOTER. | Valid credentials open a session with the stored role. Wrong password, unknown user and disabled account are refused with the same English message. Sign-out is a POST with a CSRF token. Each role lands on its own start page. |
| FR-02 | The administrator manages fictional voter accounts. | Create an account (username, display name, initial password); enable or disable it. Accounts are never deleted. Passwords are stored only as a framework hash. |
| FR-03 | The administrator creates and edits draft elections. | Title (required, at most 120 characters), description, start and end time. The start must be before the end. Times are entered in the display time zone and stored in UTC. |
| FR-04 | The administrator manages the candidates of a draft election. | Add and remove candidates while the election is a draft. Names are required and unique within the election (ignoring case). |
| FR-05 | Voter eligibility is managed per election. | The administrator grants or removes eligibility for a voter account. Only voter accounts can be eligible. Eligibility can change until voting is over. Removing it never removes an accepted vote. |
| FR-06 | Election life cycle DRAFT, OPEN, CLOSED. | Activation needs a draft, at least two candidates with different names, a valid window and an end time in the future. After activation the title, schedule and candidates are locked. Only an OPEN election can be closed, by the administrator, at any time. A closed election is never reopened. |
| FR-07 | Votes are accepted only inside the voting window, by the server clock. | Rule: `startsAt <= now < endsAt`. A vote exactly at the start is accepted; a vote exactly at the end is rejected. After the deadline votes are rejected even if nobody closed the election and no background job ran. |
| FR-08 | The voter sees their elections and their own participation status. | The home page lists activated elections where the voter is eligible or has voted, with phase, window and "voted / not voted". Drafts are never shown. The chosen candidate is never shown. |
| FR-09 | The voter votes for exactly one candidate. | View the candidates, select one, review the choice, submit. No multiple choices, ranking, proxy voting or changing a vote. |
| FR-10 | One vote per voter per election. | A second vote in the same election is rejected and changes nothing, also when both arrive at the same moment. The same voter can vote in another election where they are eligible. |
| FR-11 | Every invalid attempt is rejected with a clear English reason. | Duplicate vote, candidate of another election, missing selection, ineligible voter, disabled or non-voter account, invalid or unknown id, too early, too late, closed election. Nothing is stored by a rejected attempt. |
| FR-12 | An accepted vote returns a random confirmation code, shown once. | The code carries at least 128 random bits (implemented: 160). It is shown on the page after a Post/Redirect/Get, never in a URL, and cannot be displayed again. |
| FR-13 | Anyone can check whether a confirmation code was accepted. | The check is a POST. The answer is only "accepted", "not found" or "not a valid code". It never reveals a voter, a candidate, an election or a time. |
| FR-14 | Results are shown only after the election has ended. | Visible when the election is CLOSED or its deadline has passed. The page lists every candidate including those with zero votes, the total of accepted votes, the winner, or an explicit tie, or "no votes". While voting is possible, no per-candidate totals are available on any page or URL, not even to the administrator. |
| FR-15 | The administrator has an audit page. | It shows election creation, updates, activation and closing, candidate and eligibility changes, account changes and rejected vote attempts (account, election, reason), plus per-election counts of eligible voters, participation records, stored ballots and rejected attempts. No entry contains a candidate choice, a ballot id or a confirmation code, and accepted votes are not logged per voter. |
| FR-16 | Authorization is enforced on the server. | Every administrator URL and action requires the ADMIN role; voter pages and voting require the VOTER role. The acting user is always the authenticated principal, never a request parameter. An administrator cannot vote. |
| FR-17 | A disabled account cannot act, even with an open session. | The next request of a disabled account ends its session. The voting service checks the account state again inside the voting transaction. |
| FR-18 | Demo data and a safe reset. | The `demo` profile seeds one administrator, five voters and three elections once; restarting keeps the data. A separate script deletes only the demo database and only when explicitly confirmed. |

Out of scope by decision: public self-registration, e-mail, password recovery, deleting accounts
or elections, multiple-choice or ranked ballots, real identity checks.

## 3. Non-functional requirements

| ID | Requirement | Acceptance criteria |
|---|---|---|
| NFR-01 | Separation of identity and choice. | Eligibility and participation records hold voter and election only. A ballot holds election, candidate and the hash of the confirmation code only: no voter, username, session, IP address, timestamp or link to a participation record. No shared request id. Audit records hold no choice. |
| NFR-02 | Data integrity. | A database unique constraint on (election, voter) enforces the one-vote rule. Participation and ballot are written in one transaction: both or neither. A ballot can only name a candidate of its own election (composite foreign key). The tally is always computed from the stored ballots. |
| NFR-03 | Correct behaviour under concurrency. | Voting, closing, activation and eligibility changes for one election run one at a time (database row lock on the election). Time and status are checked again after the lock is held. The lock order is fixed and documented. A vote that could not get the lock is reported as "not processed", never as accepted. |
| NFR-04 | Basic web security. | CSRF protection on every state-changing request; no state-changing GET; output escaping; parameterised queries; role checks; password hashing; no stack traces or framework messages in error pages; passwords, confirmation codes, session cookies and choices are not logged; pages are not cacheable. |
| NFR-05 | English only. | Every page, button, label, placeholder, message and generated text is English regardless of the browser or operating system language. Documents, code comments and test names are English. |
| NFR-06 | Basic accessibility. | Labelled form fields, keyboard operation, visible focus, meaningful headings and page titles, table captions, error messages linked to fields, status never shown by colour alone, usable on phone and desktop widths. |
| NFR-07 | Time handling. | Instants are stored in UTC. Pages show times in a configurable display zone (default `America/Chicago`) with the zone abbreviation. All rules use one injectable clock. |
| NFR-08 | Local, offline demo. | Java application with an embedded server and database. No CDN, external font, paid service, external identity provider or API key. After the dependencies are downloaded once, the demo runs without a network. |
| NFR-09 | Persistence and reproducible schema. | The schema is created by versioned Flyway migrations and only validated by Hibernate. Demo data survives a restart. The H2 console is disabled. |
| NFR-10 | Testability and evidence. | Unit, component/integration and browser system tests with JUnit; security, concurrency, property-based and mutation tests; coverage and test reports are generated by documented commands. |
| NFR-11 | Auditability of rejections. | The audit record of a rejected attempt is not lost when the voting transaction rolls back, and a failing audit write can never weaken a voting rule. |

## 4. Election phases

The administrative status (DRAFT, OPEN, CLOSED) is stored. The phase shown to users is derived
from the status and the clock:

| Phase | Stored status | Clock | Votes | Results |
|---|---|---|---|---|
| Draft | DRAFT | any | rejected | hidden |
| Not started | OPEN | `now < startsAt` | rejected | hidden |
| Voting open | OPEN | `startsAt <= now < endsAt` | accepted | hidden |
| Voting ended | OPEN | `now >= endsAt` | rejected | shown |
| Closed | CLOSED | any | rejected | shown |

"Voting ended" and "Closed" differ only administratively: an ended election stopped accepting
votes because of the clock, and the administrator may still close it formally; a closed election
was stopped by an administrator, possibly before its deadline. Neither can accept votes again.
