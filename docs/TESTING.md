# Testing

This document explains how the tests are organised, what each level really exercises, and how
to reproduce every measurement. Measured results of the last run are in `docs/TEST_RESULTS.md`.

## 1. Test levels

| Level | Location | Runner | What is real | What is replaced |
|---|---|---|---|---|
| A. Unit | `src/test/java/.../unit`, `.../adversarial/unit` (`*Test`) | Surefire, JUnit Jupiter + Mockito | The class under test | Repositories and collaborating services are Mockito mocks; the clock is fixed. No Spring context, no database. |
| F. Property (pure) | `.../property` (`*Properties`) | Surefire, **jqwik** engine | The `policy` classes | Nothing; generated inputs. |
| B. Repository component | `.../component/RepositoryConstraintsIT` | Failsafe, `@DataJpaTest` | Spring Data JPA, Hibernate, Flyway schema, embedded H2 | No services, no web layer. Each test rolls back. |
| B. Web component | `.../component/WebLayerIT` | Failsafe, `@SpringBootTest` + MockMvc | Security filter chain, controllers, Thymeleaf, services, H2 | No real HTTP server or browser (mock servlet environment). The signed-in user is injected with Spring Security's test support, except in the login tests. |
| B. Service integration | `.../integration/*IT` | Failsafe, `@SpringBootTest` | Services, transactions, repositories, schema, H2 | Only the clock. `VoteRollbackIT` adds one Mockito spy and `AuditFailureIT` one Mockito mock, as described in the classes. |
| D. Security and privacy | `.../security/SecurityRegressionIT` (MockMvc), `.../security/HttpSecurityIT` (real HTTP) | Failsafe | As in the web component level; `HttpSecurityIT` uses the embedded server on a random port with real login, cookies and error dispatch | Only the clock. |
| E. Concurrency | `.../concurrency/VotingConcurrencyIT` | Failsafe | Services and H2 with one real transaction per thread | Only the clock. Test methods are not transactional. |
| F. Property (database) | `.../property/VotingSequencePropertiesIT` | Failsafe, **jqwik** engine | Services and H2, started by the test class itself | Only the clock. |
| H. Adversarial additions | `.../adversarial/**`, JUnit tag `adversarial` | Surefire and Failsafe | Same as the level each class belongs to | See each class. |
| C. System / browser | `.../system/*IT` | Failsafe, profile `system`, Selenium WebDriver + headless Chrome | Embedded server, security, templates, services, H2, a real browser, real form login | Only the clock (to reach "too early" and "too late" without waiting). |
| I. Performance smoke | `.../performance/LoadSmokeIT` | Failsafe, profile `performance` | Embedded server, real sessions, bcrypt login, CSRF, H2 | Only the clock. Client and server share one JVM. |
| G. Mutation | PIT, profile `mutation` | PIT 1.30.2 + JUnit 5 plugin 1.2.3 | Mutates `edu.comp5100.election.policy.*` | Runs only the unit-level Jupiter tests (see section 4). |

Naming is the contract: Surefire runs `*Test` and `*Properties`; Failsafe runs `*IT`. A MockMvc
test is never called a browser or end-to-end test here, and a test with mocked repositories is
never called a database test.

"Only the clock" means `TestSupportConfig` replaces the system clock with `MutableClock`. Test
accounts created by `TestData` use the `{noop}` password encoding to keep the suites fast; the
bcrypt path is covered by `ElectionAdminServiceIT.voterAccountCreation`, `DemoSeedIT` and the load
test.

## 2. Commands

Windows PowerShell (set `JAVA_HOME` to a JDK 21 first, for example
`$env:JAVA_HOME = "$env:USERPROFILE\.jdks\temurin-21"`):

```powershell
.\mvnw.cmd clean verify                                                    # levels A, B, D, E, F, H + JaCoCo
.\mvnw.cmd -Psystem verify                                                 # level C (real Chrome)
.\mvnw.cmd -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage  # level G
.\mvnw.cmd -Pperformance verify                                            # level I
.\mvnw.cmd -Pbaseline clean verify                                         # baseline suite only (no "adversarial" tag)
.\mvnw.cmd -Pmutation,baseline test-compile org.pitest:pitest-maven:mutationCoverage
.\mvnw.cmd -Pscreenshots verify                                            # documentation screenshots
.\scripts\run-ai-experiment.ps1                                            # baseline vs expanded, both measurements
```

POSIX shells (Linux, macOS, Git Bash):

```sh
export JAVA_HOME=/path/to/jdk-21
./mvnw clean verify
./mvnw -Psystem verify
./mvnw -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage
./mvnw -Pperformance verify
./mvnw -Pbaseline clean verify
./mvnw -Pmutation,baseline test-compile org.pitest:pitest-maven:mutationCoverage
./mvnw -Pscreenshots verify
```

What each profile changes (and nothing else):

| Profile | Surefire | Failsafe includes |
|---|---|---|
| none | `*Test`, `*Properties` | `**/*IT.java` except `system` and `performance` packages |
| `system` | skipped | `**/system/**/*IT.java` |
| `performance` | skipped | `**/performance/**/*IT.java` |
| `screenshots` | skipped | `**/screenshots/**/*Capture.java` |
| `baseline` | excludes tag `adversarial` | excludes tag `adversarial`; PIT uses only `unit.*` tests and writes to `target/pit-reports/baseline` |
| `mutation` | marker only | PIT is configured in `pluginManagement`, so the documented command resolves it |

Both Surefire and Failsafe are configured with `failIfNoTests=true`: a naming mistake that
selects zero tests fails the build instead of passing silently.

## 3. Reports

| Report | Path after the command |
|---|---|
| Unit and property test results | `target/surefire-reports/` |
| Integration, system, performance results | `target/failsafe-reports/` |
| JaCoCo coverage (unit + integration merged) | `target/site/jacoco/index.html`, `jacoco.xml`, `jacoco.csv` |
| PIT mutation report | `target/pit-reports/expanded/index.html` (or `baseline/`) |
| Load smoke report | `target/performance/load-smoke-report.md` |
| Copies kept with the project | `docs/evidence/` |

JaCoCo measures the default `verify` run: Surefire and Failsafe data are merged. The browser and
performance profiles are not part of the coverage figures.

## 4. Mutation testing scope

- **Mutated classes:** `edu.comp5100.election.policy.*` only: the voting rules, life cycle rules,
  tally and confirmation code logic. Entities, controllers, configuration and templates are not
  mutated, because a mutation score on boilerplate says little.
- **Tests PIT runs:** `edu.comp5100.election.unit.*` and, for the expanded suite,
  `edu.comp5100.election.adversarial.unit.*`. These are JUnit Jupiter tests. The PIT log names the
  engine of every test it uses (`[engine:junit-jupiter]`).
- **jqwik and PIT:** the jqwik property classes are deliberately outside `targetTests`. Whether
  the PIT JUnit 5 plugin would drive the jqwik engine was not relied upon; the mutation score is
  therefore the score of the Jupiter unit tests alone. The property tests are extra evidence
  that is not counted in it.
- **Integration tests and PIT:** tests that start Spring are not run by PIT (too slow per mutant).
  A mutant that only an integration test would catch counts as survived.
- **Mutators:** PIT `DEFAULTS` group. **Timeout:** PIT default factor plus 4 seconds.
- **Reporting rule:** killed, survived, no-coverage and timed-out mutants are reported
  separately. PIT's own percentage counts a timeout as a kill; this project does not.

## 5. Concurrency test method

- Each thread calls the service directly, so each thread has its own transaction. No test method
  is annotated `@Transactional`.
- "At the same time" is forced with a `CyclicBarrier` that all threads pass before calling the
  service.
- Lock races are forced with latches: the first transaction takes the lock, counts a latch down,
  and waits for a second latch before committing. The second operation is then started and must
  be seen blocked. The first latch is counted down only after the lock is held, and it is awaited
  by the test thread, not by a thread that needs the lock, so the tests cannot deadlock on their
  own latches.
- "Blocked" is observed with a bounded `Future.get(400 ms)` that must time out. This is the only
  timing-based observation; it is not used to create the race. On a very slow machine it could
  wrongly report "blocked" for an operation that is merely slow, which would be caught by the
  assertions that follow.
- Forced write failure (`VoteRollbackIT`): a Mockito spy makes the code generator return a code
  that is already stored, so the real database rejects the ballot insert with its unique
  constraint after the participation insert. The hook exists only in that test context; there is
  no production switch or HTTP endpoint for it.
- Scope: H2 2.4, READ COMMITTED. No claim is made about other database engines.

## 6. Browser tests

- Selenium WebDriver 4 with headless Chrome. Selenium Manager downloads the matching
  ChromeDriver on first use, which needs a network connection once; after that the cached driver
  (`%USERPROFILE%\.cache\selenium`) is used offline.
- Chrome must be installed. If Chrome or the driver is missing, creating the browser throws and
  the tests fail; nothing is skipped or reported as passed.
- Users sign in through the real login form. No `@WithMockUser` and no injected session.
- Waiting is condition-based; there are no fixed sleeps.

## 7. Coverage targets used during development

These are working targets chosen by the developers, not thresholds set by the course:
about 80% line coverage overall, 90% line and 85% branch coverage in the `policy` package, and a
70% mutation score in the mutation scope. The measured values are in `docs/TEST_RESULTS.md`.
Coverage shows which code was executed by a test; it does not show that the behaviour is correct.
