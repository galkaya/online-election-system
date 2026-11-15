# Implementation status

Resume point for the work: what is finished, what is not, and the commands that were really run.
Last update: 2026-11-15, commit `82ccd66` plus the documents written after it.

## 1. Environment that was set up

- No JDK or Maven was installed on the laptop. With the owner's approval, a portable Eclipse
  Temurin JDK 21.0.13 was downloaded from Adoptium, its SHA-256 checksum was verified, and it was
  unpacked to `%USERPROFILE%\.jdks\temurin-21`. No system setting, PATH entry or registry key was
  changed. The downloaded zip file was deleted after unpacking. To remove the JDK again, delete
  that folder.
- Maven 3.9.16 is downloaded by the Maven Wrapper on first use.
- Google Chrome 157 and Git 2.56 were already installed.
- A local Git repository was created in the project folder (`core.longpaths` enabled for this
  repository only, because the folder path is long). Nothing was pushed; there is no remote.

## 2. Done

| Area | State |
|---|---|
| Tool compatibility spike (Spring Boot 4.1.2, JUnit 6, jqwik 1.9.3, PIT 1.30.2) | Done; decisions in `docs/ARCHITECTURE.md` (ADR-01, ADR-02) |
| Schema (Flyway), domain model, repositories | Done |
| Business rules (`policy`), services, transactions, audit | Done |
| Security configuration, roles, CSRF, account status filter | Done |
| English user interface: sign-in, voter home, ballot, review, confirmation, code verification, administrator elections, election management, voters, results, audit, error and rejection pages | Done |
| Demo seed data, run script, safe reset script | Done |
| Unit tests (JUnit Jupiter, Mockito) | Done, 111 baseline + 24 adversarial |
| Component and integration tests (MockMvc, JPA, services with real H2) | Done |
| Security and privacy regression tests (MockMvc and real HTTP) | Done |
| Concurrency and transaction tests | Done |
| Property-based tests (jqwik), pure and with database | Done |
| Browser system tests (Selenium, headless Chrome) | Done, 19 tests |
| Mutation analysis (PIT) | Done |
| AI-assisted test design experiment, baseline against expanded | Done; `docs/ai-testing/` |
| Load smoke test | Done |
| Automated accessibility checks | Done; manual checks not performed |
| Screenshots from the running application | Done, 26 files in `docs/screenshots` |
| UML: seven diagrams as diagrams.net sources with SVG previews | Done; not yet opened in diagrams.net |
| Technical documents | Done (list in `README.md`, section 5) |
| GitHub Actions workflow file | Written; never executed |

## 3. Commands that were run for the recorded results

All on 2026-11-15, Windows 11, from the project folder, with
`JAVA_HOME=%USERPROFILE%\.jdks\temurin-21`. Results are in `docs/TEST_RESULTS.md`.

| Command | Exit code | Result |
|---|---|---|
| `.\scripts\run-ai-experiment.ps1` (runs `-Pbaseline clean verify`, baseline PIT, `clean verify`, PIT) | 0 | 238 and 286 tests passed; 92 and 96 of 99 mutants killed |
| `.\mvnw.cmd clean verify` | 0 | 147 + 139 tests passed |
| `.\mvnw.cmd surefire-report:report-only surefire-report:failsafe-report-only` | 0 | HTML test reports, copied to `docs/evidence/test-report` |
| `.\mvnw.cmd -Psystem verify` | 0 | 19 browser tests passed |
| `.\mvnw.cmd -Pperformance verify` | 0 | 1 test passed; report in `docs/evidence/performance` |
| `.\mvnw.cmd -Pscreenshots verify` | 0 | 26 screenshots written |
| `.\mvnw.cmd -o verify` | 0 | 286 tests passed in Maven offline mode |
| `.\scripts\run-demo.ps1`, stop, start again | - | Demo started; data survived the restart |
| `.\scripts\reset-demo.ps1` and `-ConfirmReset` | 2 and 0 | Refused without the switch; deleted the demo database with it |
| `java docs/uml/src/UmlGenerator.java` | 0 | Seven `.drawio` and seven `.svg` files |

The demo database was reset after the last check, so the next demo start seeds fresh data.

## 4. Not done, and open points

| Item | Why it matters | Who |
|---|---|---|
| Final report, presentation slides, ELEARN submission | Required by the course; deliberately outside this stage | Team |
| Review of all code, tests and documents | Everything was produced with an AI assistant and must be understood and checked before it is presented | Team |
| Disclosure of AI tool use in the report | See `docs/REFERENCES.md`, section 4 | Team |
| Open the `.drawio` files in diagrams.net, adjust, export | The course asks for a free UML tool; the previews were not exported by it | Team |
| Manual accessibility checks | Listed as "Not performed" | Team |
| Demo rehearsal on the presentation machine, timed | The script is untested in front of an audience | Team |
| Run the CI workflow | Needs a pushed repository; not allowed in this stage | Team, if wanted |
| Linux or macOS run of the POSIX commands | Not executed | Team, if needed |
| Effort and cost figures | No hours were recorded; see `docs/PLANNING_INPUTS.md` | Team |

## 5. Known weak spots in what exists

- The H2 defect described in `docs/TEST_RESULTS.md` (section 8) was worked around for version
  2.4.240. An H2 upgrade should be followed by a full test run.
- jqwik 1.9.3 and the PIT JUnit 5 plugin are not declared compatible with JUnit Platform 6 by
  their authors; they work here by test, not by guarantee.
- `VotingConcurrencyIT` observes "blocked" with a 400 ms wait. On a much slower machine this
  observation could be unreliable.
- The `web` package has the lowest branch coverage in the default build (68.4%).
- If the project folder is moved, run one build in the new place before the demo; the Maven and
  Selenium caches are in the user profile and are reused.

## 6. How to continue

1. Read `README.md`, then `docs/ARCHITECTURE.md` section 3 (the voting transaction).
2. Run `.\mvnw.cmd clean verify` and `.\mvnw.cmd -Psystem verify` to confirm the state above.
3. Walk through `docs/DEMO_SCRIPT.md` once.
4. Use `docs/TRACEABILITY.md` to find the code and tests for any requirement.
