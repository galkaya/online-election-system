# Test results

Recorded results of the last runs. Every number below was produced by the command next to it on
the development laptop; nothing was estimated or copied from an earlier state of the code.

## 1. Run environment

| Item | Value |
|---|---|
| Date | 2026-11-15, 16:10 to 16:25 (America/Chicago) |
| Source | Git commit `243855e`, clean working tree, for sections 2 to 6. The baseline-versus-expanded measurement in section 7 ran at commit `0651f65`; the only differences between the two commits are the stylesheet, one browser option of the test helper, and stored evidence files. |
| Machine | Laptop, Intel Core i5-10200H (8 logical processors), 16 GB RAM |
| Operating system | Windows 11 Pro, version 10.0.26200 |
| Java | Eclipse Temurin JDK 21.0.13 |
| Build tool | Apache Maven 3.9.16 through the Maven Wrapper 3.3.4 |
| Browser | Google Chrome 157 (headless) with the driver chosen by Selenium Manager |
| Libraries | Spring Boot 4.1.2, JUnit Jupiter 6.0.3, Mockito 5.23.0, jqwik 1.9.3, Selenium 4.43.0, H2 2.4.240, PIT 1.30.2 with JUnit 5 plugin 1.2.3, JaCoCo 0.8.16 |

## 2. Summary

| Command | Exit code | Tests run | Failed | Errors | Skipped | Maven time |
|---|---|---|---|---|---|---|
| `.\mvnw.cmd clean verify` (Surefire) | 0 | 147 | 0 | 0 | 0 | 56 s for the whole command |
| `.\mvnw.cmd clean verify` (Failsafe) | 0 | 139 | 0 | 0 | 0 | (same command) |
| `.\mvnw.cmd -Psystem verify` | 0 | 19 | 0 | 0 | 0 | 41 s |
| `.\mvnw.cmd -Pperformance verify` | 0 | 1 | 0 | 0 | 0 | 27 s |
| `.\mvnw.cmd -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage` | 0 | see section 5 | | | | 29 s |
| `.\mvnw.cmd -Pscreenshots verify` | 0 | 1 | 0 | 0 | 0 | 32 s |
| `.\mvnw.cmd -o verify` (Maven offline mode) | 0 | 147 + 139 | 0 | 0 | 0 | 46 s |

Total automated tests: 286 in the default build, 19 browser tests, 1 load smoke test.
No test is disabled, ignored or conditionally skipped.

## 3. Default build, suite by suite

`.\mvnw.cmd clean verify`, 2026-11-15 16:19, exit code 0.

### Surefire: 147 tests (no Spring, no database)

| Suite | Level | Engine | Tests | Result |
|---|---|---|---|---|
| `unit.ElectionLifecyclePolicyTest` | Unit | JUnit Jupiter | 35 | passed |
| `unit.VotingPolicyTest` | Unit | JUnit Jupiter | 15 | passed |
| `unit.TallyCalculatorTest` | Unit | JUnit Jupiter | 15 | passed |
| `unit.ReceiptCodecTest` | Unit | JUnit Jupiter | 17 | passed |
| `unit.ReceiptGeneratorTest` | Unit | JUnit Jupiter | 2 | passed |
| `unit.ReceiptVerificationServiceTest` | Unit | JUnit Jupiter + Mockito | 3 | passed |
| `unit.VoteTransactionTest` | Unit | JUnit Jupiter + Mockito | 15 | passed |
| `unit.VotingServiceTest` | Unit | JUnit Jupiter + Mockito | 6 | passed |
| `unit.DisplayTimeTest` | Unit | JUnit Jupiter | 3 | passed |
| `property.TallyProperties` | Property | **jqwik** | 5 properties | passed |
| `property.VotingRuleProperties` | Property | **jqwik** | 3 properties | passed |
| `property.ReceiptCodecProperties` | Property | **jqwik** | 4 properties | passed |
| `adversarial.unit.MessageCatalogTest` | Unit (adversarial) | JUnit Jupiter | 17 | passed |
| `adversarial.unit.BoundaryAndGuardTest` | Unit (adversarial) | JUnit Jupiter | 7 | passed |

A parameterised test counts once per row of its table. A jqwik property counts once, although it
runs 1000 generated cases by default (2000 where the class says so).

### Failsafe: 139 tests (Spring context and real H2 database)

| Suite | Level | Tests | Result |
|---|---|---|---|
| `component.RepositoryConstraintsIT` | Repository component (`@DataJpaTest`) | 16 | passed |
| `component.WebLayerIT` | Web component (MockMvc) | 28 | passed |
| `integration.VotingServiceIT` | Service integration | 19 | passed |
| `integration.ElectionAdminServiceIT` | Service integration | 19 | passed |
| `integration.VoteRollbackIT` | Service integration, forced write failure | 1 | passed |
| `integration.AuditFailureIT` | Service integration, failing audit log | 1 | passed |
| `integration.DemoSeedIT` | Demo seed | 3 | passed |
| `security.SecurityRegressionIT` | Security and privacy (MockMvc) | 13 | passed |
| `security.HttpSecurityIT` | Security over real HTTP | 7 | passed |
| `concurrency.VotingConcurrencyIT` | Concurrency, real transactions | 7 | passed |
| `property.VotingSequencePropertiesIT` | Property with database, **jqwik**, 25 tries | 1 property | passed |
| `adversarial.AdversarialVotingIT` | Service integration (adversarial) | 3 | passed |
| `adversarial.AdversarialWebIT` | Web component (adversarial) | 17 | passed |
| `adversarial.AdversarialHttpIT` | Real HTTP (adversarial) | 3 | passed |
| `adversarial.LogPrivacyIT` | Log privacy (adversarial) | 1 | passed |

### Browser system tests: 19 tests

`.\mvnw.cmd -Psystem verify`, 2026-11-15 16:20, exit code 0. Real headless Chrome, real login.

| Suite | Tests | Result |
|---|---|---|
| `system.ElectionEndToEndIT` (11 ordered steps of one election) | 11 | passed |
| `system.VotingWindowBrowserIT` (too early, at the deadline, just before the deadline) | 3 | passed |
| `system.AccessibilityChecksIT` (automated DOM and keyboard checks) | 5 | passed |

## 4. Coverage (JaCoCo)

From the default build above (Surefire and Failsafe data merged; browser and load tests are not
included). Report copy: `docs/evidence/jacoco-html/index.html`.

| Scope | Lines covered | Branches covered |
|---|---|---|
| Whole application | 876 of 921 (95.1%) | 261 of 290 (90.0%) |
| `policy` (business rules) | 162 of 164 (98.8%) | 113 of 114 (99.1%) |
| `service` | 335 of 336 (99.7%) | 101 of 114 (88.6%) |
| `web` | 153 of 187 (81.8%) | 26 of 38 (68.4%) |
| `security` | 27 of 27 (100%) | 13 of 16 (81.2%) |
| `config` | 74 of 74 (100%) | 8 of 8 (100%) |
| `domain` | 124 of 129 (96.1%) | no branches |
| `ElectionApplication` (main method) | 1 of 4 (25%) | no branches |

Against the working targets in `docs/TESTING.md` (80% lines overall; 90% lines and 85% branches
in `policy`): all met. The `web` package has the lowest branch coverage; the uncovered branches
are mostly form-error paths of the administrator pages that only the browser suite exercises,
and the browser suite is not part of this measurement. The `main` method is not executed by
tests.

Coverage says which code ran during a test. It does not say that the result was checked.

## 5. Mutation analysis (PIT)

`.\mvnw.cmd -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage`, run by
`scripts/run-ai-experiment.ps1` on 2026-11-15 16:14. Report copy:
`docs/evidence/ai-experiment/pit-expanded-html/index.html`.

- Scope: 12 classes in `edu.comp5100.election.policy`, mutator group DEFAULTS.
- Tests used by PIT: 12 JUnit Jupiter test classes (`unit.*` and `adversarial.unit.*`). The PIT
  log names the engine of the tests it ran: `[engine:junit-jupiter]`. jqwik properties and the
  Spring tests are not run by PIT.

| Mutants | Count |
|---|---|
| Generated | 99 |
| Killed by a failing test | 96 (97.0%) |
| Timed out | 1 |
| Survived | 2 |
| No coverage | 0 |

PIT prints "97 killed (98%)" because it counts the timeout as detected. Here it is listed
separately.

- **Timed out (1):** `ReceiptCodec.encode`, "changed increment from -5 to 5". The mutation turns
  the bit loop into an endless loop. In one of the three recorded runs of the expanded suite the
  same mutant ended as a memory error instead of a timeout. Either way the mutant cannot produce a code, but it was
  stopped by PIT, not by an assertion.
- **Survived (2), both judged equivalent:**
  - `ReceiptCodec.encode`, "replaced shift left with shift right" in the line that clears already
    emitted bits from the buffer. The emitted characters only ever read the lowest twelve bits,
    so not clearing the higher bits cannot change any output.
  - `ReceiptCodec.normalize`, "removed conditional" on the early exit for input longer than 32
    characters. The final length check rejects the same inputs.
  No exclusion was added for them; they remain in the denominator.

## 6. Load smoke test

`.\mvnw.cmd -Pperformance verify`, 2026-11-15 16:20, exit code 0. Full report:
`docs/evidence/performance/load-smoke-report.md`.

60 voters with real sessions, bcrypt login and CSRF tokens, 12 concurrent client threads, after a
warm-up of 12 voters; client and server in one JVM on the laptop described above.

| Check | Result |
|---|---|
| Voter flows that succeeded | 60 of 60 |
| Participation records / ballots stored | 60 / 60 |
| Tally equals the votes sent | yes (20 / 20 / 20) |
| Duplicate attempts rejected with 409 | 10 of 10 |

| Successful request | p50 | p95 | max |
|---|---|---|---|
| POST /login (bcrypt, new session) | 256.8 ms | 321.5 ms | 336.5 ms |
| GET ballot page | 19.1 ms | 50.7 ms | 77.2 ms |
| POST vote (accepted) | 20.5 ms | 81.8 ms | 136.4 ms |
| Whole voter flow (5 requests) | 400.6 ms | 492.4 ms | 532.9 ms |

This is a smoke test, not a benchmark: one short run on a laptop that was also running the test
client. It supports no capacity or response-time claim beyond "60 voters at this concurrency
were handled correctly on this machine".

## 7. Baseline against expanded suite

Generated by `scripts/run-ai-experiment.ps1` at commit `0651f65` (clean working tree),
2026-11-15 16:14. Raw reports: `docs/evidence/ai-experiment/`. Interpretation:
`docs/ai-testing/04-results.md`.

| Measure | Baseline suite | Expanded suite |
|---|---|---|
| Tests in the default build | 238 | 286 |
| Lines covered, whole application | 876 of 921 (95.1%) | 876 of 921 (95.1%) |
| Branches covered, whole application | 261 of 290 (90.0%) | 261 of 290 (90.0%) |
| Mutants killed (of 99) | 92 (92.9%) | 96 (97.0%) |
| Mutants timed out / survived / no coverage | 1 / 4 / 2 | 1 / 2 / 0 |
| `verify` time | 55 s | 59 s |
| PIT time | 29 s | 32 s |

## 8. Defects found

| # | Kind | Found by | Description | Fix | Regression test |
|---|---|---|---|---|---|
| 1 | **Real defect** in the schema as first written | `RepositoryConstraintsIT` (baseline suite), all tests failing in set-up | With H2 2.4.240, the CHECK constraints `role IN ('ADMIN','VOTER')` and `status IN (...)` fail with error 23514 ("database has been closed") for every insert once the connection that created the table is closed. In the demo this would be expected to start when the connection pool retires the connection that created the schema (HikariCP's default maximum connection lifetime is 30 minutes) on the first run with a new database; that was not waited for in the running application. The failure itself was reproduced outside the application with a short JDBC program: create the table on one connection, close it, insert on a second connection. | Both checks now use `REGEXP_LIKE` (`V1__create_schema.sql`, ADR-05). | `RepositoryConstraintsIT#roleAndStatusValuesAreCheckedByTheDatabase` |
| 2 | Test defect | First run of `HttpSecurityIT` | The test HTTP client sent no `Accept` header, so error responses came back as JSON instead of the HTML error page. | The client sends a browser-like `Accept` header. | - |
| 3 | Test defect | First run of `WebLayerIT` and `AccessibilityChecksIT` | A wrong expected redirect pattern; and a keyboard test that assumed the first Tab stop on a page with an autofocus field. | Expectations corrected; the Tab test uses real key presses on a page without autofocus. | - |
| 4 | Presentation issue seen in a screenshot | Review of `19-admin-audit.png` | The count table on the audit page was wider than the page because header cells could not wrap. | Header cells wrap (`app.css`). | Screenshot retaken |

No defect was found by the adversarial tests, by a PIT mutant that pointed to wrong production
behaviour, or by an artificially seeded fault; no fault was seeded into the code at any time.
The four surviving or uncovered baseline mutants pointed to missing assertions, not to defects.

## 9. Checks made by hand or by script, outside the test suites

| Check | How | Result |
|---|---|---|
| Demo start from the build | `.\scripts\run-demo.ps1` (the `spring-boot:run` command with the `demo` profile), fresh database | Started; Flyway created the schema; the seed log line reported 1 administrator, 5 voters, 3 elections; sign-in as `admin` worked in a browser. |
| Demo data survives a restart | Activated the seeded election, stopped the application, started it again | Election still "Voting open"; log line "Demo data already present; nothing was changed." |
| Demo reset | `.\scripts\reset-demo.ps1` without and with `-ConfirmReset` | Without: exit code 2, nothing deleted. With: the database file was deleted. |
| Offline build | `.\mvnw.cmd -o verify` (Maven offline mode; the network cable was not unplugged) | Exit code 0, 286 tests passed. |
| No external resources in pages | Search of templates and static files for external addresses | None found (only XML namespace declarations). |
| English only | Search of all source, template, script and document files for Turkish letters. The MockMvc and browser tests assert English texts on every kind of page. Eight pages were also looked at by eye, in screenshots or in a live browser (sign-in, elections list, election management, ballot, confirmation, results, audit, phone-width voter home). | No Turkish text found; the pages looked at were English throughout. The other 14 application screenshots were not inspected one by one. |
| Traceability references | Script comparing every `Class#method` in `docs/TRACEABILITY.md` and `docs/ai-testing/03-candidate-decisions.md` with the sources | All references exist. |
| UML files | XML parse of the seven `.drawio` files; SVG previews rendered in Chrome and viewed | Well-formed; previews readable. |

## 10. Not run, not done

| Item | Status |
|---|---|
| GitHub Actions workflow (`.github/workflows/ci.yml`) | **NOT RUN.** The repository was not pushed anywhere. |
| Build and tests on Linux or macOS, POSIX commands and `reset-demo.sh` | **NOT RUN.** Written, not executed. (`mvnw` itself was run through Git Bash on Windows.) |
| Demo with the network physically disconnected | **NOT RUN.** Only Maven's offline mode was tested. |
| Opening the `.drawio` files in diagrams.net and exporting from it | **NOT DONE.** |
| Manual accessibility review (screen reader, contrast measurement, zoom) | **NOT DONE.** See `docs/ACCESSIBILITY_CHECKLIST.md`. |
| Browsers other than Chrome | **NOT RUN.** |
| Databases other than H2 | **NOT RUN.** Not supported. |
| Penetration test or security review by a person | **NOT DONE.** |
| Review of code, tests and documents by the team members | **PENDING.** |
| Independent repetition of the AI test-design prompt with a fresh model instance | **NOT DONE.** |

## 11. Size

Counted from the files tracked by Git at commit `243855e`.

| Part | Files | Lines |
|---|---|---|
| Production Java (`src/main/java`) | 66 | 2,970 |
| Test Java (`src/test/java`) | 41 | 6,667 |
| Thymeleaf templates | 18 | 872 |
| Stylesheet | 1 | 819 |
| SQL migration | 1 | 90 |
