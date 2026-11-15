# Online Election System

COMP 5100 Software Engineering semester project by Gokdeniz Alkaya and Derrick Martin.

A web application for a **simulated election with fictional voters**: an administrator sets up
an election, eligible voters sign in and cast one ballot each, every accepted vote gets a random
confirmation code, and after the election ends the tally and an audit page are available.

> This is an academic prototype. It is **not** a certified system for real public elections and
> it does **not** provide end-to-end cryptographic verification or guaranteed ballot secrecy.
> See `docs/SECURITY_AND_LIMITATIONS.md`.

Technology: Java 21, Spring Boot 4.1.2, Spring Security, Thymeleaf, H2, Flyway, Maven.
Tests: JUnit Jupiter 6, Mockito, jqwik, Selenium WebDriver, PIT, JaCoCo.

## 1. What you need

| Requirement | Notes |
|---|---|
| JDK 21 | Any JDK 21. On the development laptop a portable Eclipse Temurin 21.0.13 is unpacked in `%USERPROFILE%\.jdks\temurin-21` (nothing was installed system-wide). |
| Internet access, once | The first build downloads Maven (through the Maven Wrapper), the libraries and, for the browser tests, the Chrome driver. After that everything runs offline. |
| Google Chrome | Only for the browser tests and the screenshot run. |

Maven does not need to be installed: use the wrapper scripts `mvnw.cmd` (Windows) or `./mvnw`.

If the project sits in a very deep folder on Windows, Git may report "Filename too long"; run
`git config core.longpaths true` in the project folder.

## 2. Run the demo (Windows PowerShell)

```powershell
# once per terminal, unless JAVA_HOME is already set
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\temurin-21"

.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

or simply `.\scripts\run-demo.ps1`, which finds the JDK and runs the same command.

Open <http://localhost:8080>. Stop with Ctrl+C. The demo data is stored in
`data\demo\election-demo.mv.db` and survives restarts.

POSIX shells:

```sh
export JAVA_HOME=/path/to/jdk-21
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

### Demo accounts

These accounts are fictional. They exist only in the local demo database, which is created by
the `demo` profile. Do not reuse the passwords anywhere.

| Username | Password | Role | Purpose in the demo |
|---|---|---|---|
| `admin` | `Admin-Demo-2026` | Administrator | Creates, activates and closes elections; results and audit. |
| `voter1` | `Voter-Demo-2026` | Voter | Eligible; first vote. |
| `voter2` | `Voter-Demo-2026` | Voter | Eligible; second vote, for a different candidate. |
| `voter3` | `Voter-Demo-2026` | Voter | Eligible; tries to vote after the close and after a deadline. |
| `voter4` | `Voter-Demo-2026` | Voter | Not eligible for any election. |
| `voter5` | `Voter-Demo-2026` | Voter | Eligible on paper, but the account is disabled. |

Seeded elections (dates are relative to the moment the database was first created):

| Election | State | Purpose |
|---|---|---|
| Student Council President 2026 | Draft, three candidates, voters 1, 2, 3 and 5 eligible, window from seeding time to 14 days later | Main demo: activate, vote, close, tally. |
| Library Hours Referendum | Activated, deadline passed one day before seeding, two votes | Shows rejection after a deadline without closing, and a result with a winner. |
| Spring Festival Theme Vote | Activated, starts seven days after seeding | Shows the "not started" state. |

### Reset the demo

Stop the application, then:

```powershell
.\scripts\reset-demo.ps1 -ConfirmReset
```

(`./scripts/reset-demo.sh --confirm-reset` on POSIX.) The script deletes only
`data\demo\election-demo.*` inside this project, refuses to run without the confirmation switch,
and refuses while the application is running. Starting the demo again creates fresh data.

The step-by-step presentation is in `docs/DEMO_SCRIPT.md`.

## 3. Tests

```powershell
.\mvnw.cmd clean verify                                                    # unit, component, integration, security, concurrency, property tests + coverage
.\mvnw.cmd -Psystem verify                                                 # browser tests (real headless Chrome)
.\mvnw.cmd -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage  # mutation analysis
.\mvnw.cmd -Pperformance verify                                            # local HTTP load smoke test
```

POSIX: the same with `./mvnw`.

| Report | Location after the run |
|---|---|
| Test results | `target\surefire-reports`, `target\failsafe-reports` |
| Coverage | `target\site\jacoco\index.html` |
| Mutation analysis | `target\pit-reports\expanded\index.html` |
| Load smoke report | `target\performance\load-smoke-report.md` |

What each level really tests is explained in `docs/TESTING.md`. The measured results of the last
recorded run, with date and commit, are in `docs/TEST_RESULTS.md`; copies of the reports are in
`docs/evidence`.

## 4. Project layout

```
pom.xml, mvnw, mvnw.cmd          build, with profiles system / performance / mutation / baseline / screenshots
scripts/                         run-demo, reset-demo, run-ai-experiment
src/main/java/edu/comp5100/election/
    domain/                      JPA entities
    repository/                  Spring Data JPA repositories
    policy/                      pure business rules (voting, life cycle, tally, confirmation codes)
    service/                     transactions, administration, queries, audit
    security/, config/           login, account filter, security and clock configuration, demo seed
    web/                         controllers, forms, error pages
src/main/resources/
    db/migration/                Flyway schema
    templates/, static/          Thymeleaf pages, stylesheet (no external resources)
src/test/java/edu/comp5100/election/
    unit/                        JUnit + Mockito, no Spring
    property/                    jqwik properties
    component/, integration/     MockMvc, JPA and service tests with a real H2 database
    security/, concurrency/      security regression and race tests
    adversarial/                 AI-assisted additional tests (JUnit tag "adversarial")
    system/                      Selenium browser tests
    performance/, screenshots/   load smoke test, screenshot capture
docs/                            requirements, architecture, testing, results, UML, screenshots, evidence
```

## 5. Documents

| File | Content |
|---|---|
| `docs/REQUIREMENTS.md` | Actors, functional and non-functional requirements, acceptance criteria. |
| `docs/TRACEABILITY.md` | Requirement to code, tests and demo steps. |
| `docs/ARCHITECTURE.md` | Layers, data model, voting transaction, lock order, privacy trade-offs, decisions. |
| `docs/TESTING.md` | Test levels, profiles, scopes, how to reproduce. |
| `docs/TEST_RESULTS.md` | Results of the last recorded runs; what was not run. |
| `docs/ai-testing/` | The AI-assisted test design experiment: prompt, candidates, decisions, results. |
| `docs/SECURITY_AND_LIMITATIONS.md` | Threats considered and guarantees that are not given. |
| `docs/ACCESSIBILITY_CHECKLIST.md` | Automated and manual accessibility checks with their real status. |
| `docs/DEMO_SCRIPT.md` | A demo of about nine minutes. |
| `docs/COURSE_ALIGNMENT.md` | Relation to the course topics and to the project description. |
| `docs/PLANNING_INPUTS.md` | Facts, assumptions and risks for the later cost analysis. |
| `docs/REFERENCES.md` | Sources, third-party material and the disclosure of AI tool use. |
| `docs/uml/` | UML diagrams: editable diagrams.net files and SVG previews. |
| `docs/screenshots/` | Screenshots taken from the running application by a real browser. |
| `docs/IMPLEMENTATION_STATUS.md` | What is done, what is not, and the commands that were run. |

## 6. Known limitations

- Simulation only: fictional accounts created by the administrator, local passwords, no identity
  checks, no password recovery, no rate limiting.
- No ballot secrecy guarantee: the application never shows or stores who voted for whom, but a
  person with database or server access could correlate records. Details and further limits are
  in `docs/SECURITY_AND_LIMITATIONS.md`.
- A confirmation code only shows that a ballot was accepted. It is shown once and cannot be
  recovered.
- All votes of one election are processed one after another (a database row lock). This is fine
  for a classroom demo and is not designed for large elections.
- H2 is the only supported database. The concurrency tests show the behaviour on H2 only.
- The default profile (without `demo`) starts with an empty in-memory database and no accounts;
  it exists only as a start-up check. Use the `demo` profile.
- The user interface is English only.
- The final report, the presentation slides and the ELEARN submission are not part of this
  repository yet.

## 7. Use of AI tools

The code, tests and documents in this repository were written with an AI coding assistant and
must be reviewed by the team before they are presented as the team's work. The details that
have to be disclosed are in `docs/REFERENCES.md`, section 4.
