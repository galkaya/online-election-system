# Candidate decisions

Decision rules:

- **Covered**: a baseline test already checks the same expectation. No new test; it is **not**
  counted as a gain of the experiment.
- **Accepted**: turned into an executable test in `src/test/java/edu/comp5100/election/adversarial`
  (JUnit tag `adversarial`). Expected values come from the requirement or from a hand
  calculation, not from running the code first.
- **Rejected**: not implemented, with the reason.

"First run" is the result of the new test the first time it was executed against the unchanged
production code.

Review status for every row: decided and implemented by the AI assistant on 2026-11-15;
**review by the team members: pending**.

| ID | Decision | Baseline test that already covers it, or reason | Implemented as | First run |
|---|---|---|---|---|
| C01 | Covered | `VotingConcurrencyIT#simultaneousVotesOfOneVoter` | - | - |
| C02 | Accepted | The baseline tests the race at the service only. | `AdversarialHttpIT#simultaneousDuplicateOverRealHttpSessions` | passed |
| C03 | Covered | `ElectionLifecyclePolicyTest#votingWindowBoundaries`, `VotingServiceIT#votingWindowIsEnforcedByTheServerClock` | - | - |
| C04 | Accepted | No baseline test has both boundaries one tick apart. | `BoundaryAndGuardTest#oneNanosecondWindow` | passed |
| C05 | Accepted | The baseline never moves the clock while a vote waits for the lock. | `AdversarialVotingIT#deadlinePassesWhileTheVoteWaitsForTheLock` | passed |
| C06 | Covered | `ElectionLifecyclePolicyTest#activationRefusedAfterDeadline`, `ElectionAdminServiceIT#expiredDraftCannotBeActivated` | - | - |
| C07 | Covered | `VotingServiceIT#candidateOfAnotherElectionIsRejected`, `SecurityRegressionIT#candidateOfAnotherElectionIsRefused`, `RepositoryConstraintsIT#ballotCandidateMustBelongToTheBallotElection` | - | - |
| C08 | Accepted | The baseline tries one non-numeric candidate id and one non-numeric election id only. | `AdversarialWebIT#hostileCandidateIdsAreRefused`, `#impossibleElectionIdsAreNotFound`, `#malformedElectionIdsAreBadRequests` | passed |
| C09 | Covered | `VoteRollbackIT#failedBallotWriteLeavesNoHalfVote` | - | - |
| C10 | Rejected | The application cannot produce this state (C09 proves it), and the expected answer is the ordinary duplicate rule that C01 and others already test. | - | - |
| C11 | Covered | `VotingConcurrencyIT#closeFirstThenWaitingVoteIsRejected`, `#voteFirstThenWaitingCloseSucceeds` | - | - |
| C12 | Rejected | Same rule as C11 on a slower and less controllable level; the lock cannot be held deterministically from outside an HTTP request. | - | - |
| C13 | Covered | `SecurityRegressionIT#disabledAccountWithOpenSessionCannotVote`, `VotingConcurrencyIT#disableFirstThenWaitingVoteIsRejected` | - | - |
| C14 | Accepted | The baseline disables and re-enables an account but never votes afterwards. | `AdversarialVotingIT#reEnabledAccountVotesExactlyOnce` | passed |
| C15 | Accepted | No baseline test looks at log output. | `LogPrivacyIT#sensitiveValuesAreNotLogged` | passed |
| C16 | Covered | `WebLayerIT#voteUsesPostRedirectGetAndShowsCodeOnce`, `HttpSecurityIT#voteAndVerifyOverRealHttp`, `ElectionEndToEndIT#firstVoterVotesAndReceivesCode` | - | - |
| C17 | Accepted | The baseline checks that the answer omits voter and candidate, not that two answers are identical. | `AdversarialWebIT#verificationAnswerDoesNotDependOnTheBallot` | passed |
| C18 | Accepted | The baseline checks this for an ineligible voter (`VotingServiceIT#rejectionAuditDoesNotContainTheChoice`), not for the foreign-candidate case where the id is the very thing being rejected. | `AdversarialVotingIT#foreignCandidateAttemptIsAuditedWithoutTheCandidate` | passed |
| C19 | Covered | `SecurityRegressionIT#noInterimResultsAnywhere`, `ElectionAdminServiceIT#resultsAreHiddenUntilVotingIsOver`, `WebLayerIT#resultsAreNotAvailableDuringVoting` | - | - |
| C20 | Rejected | Not a testable expectation; it is a property of small elections. Recorded as a limitation in `docs/SECURITY_AND_LIMITATIONS.md`. | - | - |
| C21 | Covered | `TallyCalculatorTest#tieIsReportedAsTie`, `#noVotesMeansNoWinner`, `#singleBallot`, `ElectionAdminServiceIT#tieAndEmptyResults` | - | - |
| C22 | Covered | `TallyCalculatorTest#unknownCandidateIsAnIntegrityError` | - | - |
| C23 | Accepted | No baseline unit test reads a message text; the MockMvc tests do, but PIT does not run them. | `MessageCatalogTest` (five test methods) | passed |
| C24 | Accepted | The baseline vectors set at most one byte. | `BoundaryAndGuardTest#denseBitPatternsEncodeCorrectly`, `#denseCodesRoundTrip` | passed |
| C25 | Covered | `ReceiptCodecTest#rejectsMalformedInput` contains a 33-character input. The surviving mutant behind this candidate is equivalent (see `04-results.md`), so a longer input would not add anything. | - | - |
| C26 | Accepted | The baseline only tries zero of zero. | `BoundaryAndGuardTest#percentWithoutTotalIsZero` | passed |
| C27 | Accepted | The baseline tests a missing and a corrupted token, not a valid token of another session. | `AdversarialHttpIT#csrfTokenOfAnotherSessionIsRefused` | passed |
| C28 | Accepted | Not tested in the baseline. | `AdversarialHttpIT#sessionIdChangesAtLogin` | passed |
| C29 | Covered | `ElectionAdminServiceIT#eligibilityManagement`, `VotingServiceIT#revokedEligibilityIsRejected`, `VotingConcurrencyIT#revokeFirstThenWaitingVoteIsRejected`, and the grant-then-vote path in most integration tests. | - | - |
| C30 | Covered | `ElectionEndToEndIT#duplicateSubmissionIsRejected` (second browser tab) | - | - |
| C31 | Covered | `WebLayerIT#invalidLoginsAreRefused`, `VotingServiceIT#disabledAccountIsRejected` | - | - |
| C32 | Covered | `VotingServiceIT#administratorCannotVote`, `VoteTransactionTest#administratorCannotVote` | - | - |
| C33 | Rejected | Neither the form (four-digit years) nor the database column can hold these values, so the test would exercise a situation that cannot occur. | - | - |
| C34 | Rejected | A response-time comparison is not reliable inside a unit or integration test on a shared laptop. The lookup is a single indexed query on a hash; no measurement was made, so no claim is made either way. | - | - |
| C35 | Accepted | The baseline tests escaping of stored text, not reflection of the verification input. | `AdversarialWebIT#receiptInputIsNotReflected` | passed |
| C36 | Covered | `ElectionEndToEndIT#voteAfterCloseIsRejected` (page opened before the close, in a second browser) | - | - |

## Totals

| Outcome | Count | Candidates |
|---|---|---|
| Already covered by the baseline | 17 | C01, C03, C06, C07, C09, C11, C13, C16, C19, C21, C22, C25, C29, C30, C31, C32, C36 |
| Accepted and implemented | 14 | C02, C04, C05, C08, C14, C15, C17, C18, C23, C24, C26, C27, C28, C35 |
| Rejected | 5 | C10, C12, C20, C33, C34 |

Every accepted test passed on its first run against the unchanged production code. The
adversarial pass therefore found **no new defect** in the application.
