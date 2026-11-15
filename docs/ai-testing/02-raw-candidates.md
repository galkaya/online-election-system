# Raw candidate tests

The 36 candidates below are the suggestions as they were produced on 2026-11-15, before any
accept or reject decision. They were transcribed from the session into this table; identifiers
were assigned at transcription. Nothing was removed.

| ID | Requirement | Scenario | Expected result (from the requirement) | Why it matters |
|---|---|---|---|---|
| C01 | FR-10, NFR-02 | N threads call the voting service for the same voter and election at the same moment. | Exactly one vote is accepted; one participation, one ballot; the others get "already voted". | The one-vote rule must hold without relying on timing. |
| C02 | FR-10, NFR-02 | The same account is signed in from several real HTTP sessions and all submit a vote at once. | Exactly one request is answered with the confirmation redirect; the others with 409; one ballot. | The rule must hold through the whole web stack, where each session has its own CSRF token and thread. |
| C03 | FR-07 | Vote at exactly `endsAt`, and one tick before it. | At `endsAt`: rejected as "ended". One tick before: accepted. | An off-by-one at the deadline accepts late votes. |
| C04 | FR-07 | The smallest possible window: `endsAt = startsAt + 1 ns`. | Only the start instant is accepted; the end instant is rejected. | Both boundaries meet; a wrong comparison on either side shows immediately. |
| C05 | FR-07, NFR-03 | A vote is sent before the deadline but has to wait for the election lock; the deadline passes while it waits. | Rejected as "ended": the decision uses the time at which the vote is processed. | A queued vote must not slip in after the deadline. |
| C06 | FR-06 | Activate a draft when the clock is exactly at `endsAt`. | Refused: the deadline has passed. | An election that can never accept a vote must not be opened. |
| C07 | FR-11 | Submit a candidate id that belongs to another election, at the service and over HTTP. | Rejected as "not on the ballot"; neither election stores anything. | Cross-election ids are the classic object reference attack. |
| C08 | FR-11, NFR-04 | Hostile id values for candidate and election: negative, zero, larger than a 64-bit number, exponent notation, SQL-shaped text, lists. | 400 or 404 with a plain English page; nothing stored; no exception text. | Malformed input must fail safely, not with a stack trace or a stored vote. |
| C09 | NFR-02 | The ballot insert fails after the participation insert. | The participation is rolled back; the voter can vote again later. | A half-written vote would block the voter or distort the counts. |
| C10 | NFR-02 | A participation row without a ballot is planted directly in the database, then the voter votes. | Rejected as "already voted"; no ballot is created. | Shows what happens with inconsistent data. |
| C11 | FR-06, NFR-03 | Vote and close race, both orders forced with latches. | Close first: the vote is rejected. Vote first: the vote is counted, then the close succeeds. | The documented ordering contract. |
| C12 | FR-06, NFR-03 | The same race driven through HTTP requests. | Same as C11. | End-to-end confirmation. |
| C13 | FR-17 | A session opened before the account was disabled submits a vote. | The session is ended; nothing is stored. | Disabling must take effect immediately. |
| C14 | FR-02, FR-17 | Disable an account, attempt to vote, re-enable it, vote, vote again. | Refused, then accepted exactly once, then "already voted". | Re-enabling must not open a path to a second vote or leave the account blocked. |
| C15 | NFR-04 | Capture the application log during login (right and wrong password), a vote, a duplicate attempt and a verification. | The log contains no password, no confirmation code, no candidate name or id. | Logs are the most common place for accidental leaks. |
| C16 | FR-12 | Look for the code in the redirect target, the page address and the page after a reload. | The code appears only in the page body right after voting. | Codes in URLs end up in histories and logs. |
| C17 | FR-13, NFR-01 | Verify two accepted codes that belong to different voters, candidates and elections. | The two answers are identical. | Any difference would be an oracle for the content of a ballot. |
| C18 | FR-15, NFR-01 | A rejected attempt with a foreign candidate id. | The audit record holds account, election and reason, but not the submitted candidate. | The audit log must not record intended choices. |
| C19 | FR-14 | Request results while voting is open, as administrator and as voter, and read the administrator pages. | No per-candidate totals anywhere. | Interim results influence voters. |
| C20 | NFR-01 | An election with a single voter: the published result reveals the choice. | (No pass/fail expectation.) | A real limitation of any small election. |
| C21 | FR-14 | Tie, all candidates at zero, a single ballot. | Tie reported as a tie; no winner without votes. | No arbitrary winner. |
| C22 | NFR-02 | The tally receives a ballot for a candidate that is not on the ballot. | An integrity error, not a silent skip. | Silent skipping would hide corruption. |
| C23 | FR-11, NFR-05 | Every rejection reason, activation problem and phase label. | Each has a non-empty, distinct, English text. (Baseline PIT: mutants that blank these texts survive or are uncovered at unit level.) | A rejection without an explanation fails FR-11. |
| C24 | FR-12 | Encode byte patterns in which every bit position alternates (0xAA, 0x55, 0x0F). | The hand-computed character sequences. (Baseline PIT: a surviving mutant in the bit-buffer masking.) | The baseline vectors are mostly zero bytes and may miss carry errors across byte boundaries. |
| C25 | FR-13 | Code input of 33 and of 10,000 valid characters. | Rejected as malformed. (Baseline PIT: surviving mutant on the early length exit.) | Input length handling. |
| C26 | FR-14 | Percentage with a total of zero and a positive count. | 0.0, never NaN or infinite. (Baseline PIT: surviving mutant on the zero-total guard.) | The guard is defensive; a NaN on the results page would be a visible defect. |
| C27 | NFR-04 | Use the CSRF token of one session in another session of the same account. | 403; nothing stored. | Tokens must be bound to the session. |
| C28 | NFR-04 | Compare the session id before and after login. | It changes. | Session fixation. |
| C29 | FR-05 | Grant eligibility after a rejected attempt, then vote; revoke during voting. | Accepted after the grant; rejected after the revocation. | Eligibility changes take effect immediately. |
| C30 | FR-10 | The same form submitted from two browser tabs. | The second submission is rejected. | The realistic way a duplicate happens. |
| C31 | FR-17 | A voter who is eligible but disabled. | Cannot sign in and cannot vote. | Eligibility does not override the account state. |
| C32 | FR-16 | An administrator account with an eligibility row forced into the database. | Still cannot vote. | The role rule must not depend on the absence of a row. |
| C33 | FR-07 | Window rules with `Instant.MIN` and `Instant.MAX`. | No exception; consistent answers. | Extreme values. |
| C34 | FR-13 | Compare the response time of "accepted" and "not found". | No usable difference. | Timing side channel. |
| C35 | NFR-04 | Type markup into the verification field. | It is not reflected into the page. | Reflected cross-site scripting. |
| C36 | FR-06 | A voter opens the ballot before the close and submits after it. | Rejected as "closed". | The realistic late vote. |
