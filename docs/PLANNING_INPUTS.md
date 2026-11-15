# Planning inputs for the later cost analysis

This file collects facts and clearly labelled assumptions that the final report's cost analysis
can use. It is not the cost analysis. Where a number was not measured, it says so.

## 1. Measured facts (from the repository and the build)

The size and test figures are produced by commands and are listed with their date in
`docs/TEST_RESULTS.md`. They are measurements of the product, not of effort.

| Fact | How to obtain it |
|---|---|
| Lines of production Java, test Java, templates, SQL | `git ls-files` plus a line count; see `docs/TEST_RESULTS.md` section "Size" |
| Number of automated tests per suite | Surefire and Failsafe summaries |
| Build and test durations on the development laptop | Maven "Total time" lines, `docs/evidence/ai-experiment/summary.md` |
| Coverage and mutation results | JaCoCo and PIT reports |

## 2. Effort

**No working hours were recorded for this project.** The application, tests and documents in
this repository were produced with an AI coding assistant (see `docs/REFERENCES.md`) in a small
number of long sessions on 2026-11-14 and 2026-11-15, directed and reviewed by the team. The
following are unknown and must not be invented in the report:

- hours spent by each team member on the first report, on directing and reviewing the
  implementation, on preparing the demo and on the final report;
- the usage cost of the AI assistant (no cost figure was available in the development
  environment).

If the cost analysis needs an effort figure, two honest options are:

1. Record the remaining hours from now on (report writing, UML review, rehearsal) and state that
   earlier hours are an estimate from memory.
2. Estimate what a conventional team would need from the measured size, with a named method
   (for example basic COCOMO in organic mode on the measured lines of code) and state clearly
   that this is a model estimate of a hypothetical conventional development, not a record of
   what happened.

## 3. Tool costs

All tools are free of licence cost for this use. "Free" is not the same as "no cost":

| Item | Licence cost | Real cost to account for |
|---|---|---|
| JDK 21 (Eclipse Temurin), Maven, Spring Boot, H2, Thymeleaf, JUnit, Mockito, jqwik, PIT, JaCoCo, Selenium | none (open-source licences) | Time to learn, configure and update them. The JUnit 6 compatibility check for jqwik and PIT and the H2 constraint defect each cost real investigation time. |
| diagrams.net (draw.io) | none | Time to draw and review diagrams. |
| Git | none | Hosting is not used; a private remote would be free at this size. |
| Google Chrome for browser tests | none | Must be installed on the demo machine. |
| AI coding assistant | **unknown** (subscription or usage based; not visible in the session) | Subscription or usage fees, plus review time for everything it produced. |
| Development laptop | already owned | Depreciation and electricity are normally ignored at this scale; say so if you ignore them. |
| Hosting | none | The system runs only locally. A hosted demo would add server, domain and TLS costs that were deliberately avoided. |

## 4. Remaining work and dependencies

| Work item | Depends on | Notes |
|---|---|---|
| Review of code, tests and documents by both team members | nothing | Needed before anything is presented as the team's work. The review column in `docs/ai-testing/03-candidate-decisions.md` is still "pending". |
| Open the UML files in diagrams.net, adjust the layout, export images | diagrams.net | The `.drawio` sources and generated SVG previews exist; an export from diagrams.net itself has not been made. |
| Manual accessibility checks | a screen reader, a contrast tool | Listed as "Not performed" in `docs/ACCESSIBILITY_CHECKLIST.md`. |
| Demo rehearsal on the presentation machine | JDK 21, Chrome, one online build | Follow `docs/DEMO_SCRIPT.md`; time it. |
| Final report | everything above | Not started. |
| Presentation slides | final report outline, screenshots | Not started. |
| ZIP submission | final report, slides | Exclude `target/` and `data/`. |

## 5. Risks

| Risk | Likelihood | Effect | Response |
|---|---|---|---|
| Demo machine has no JDK 21 or Chrome | medium | Demo cannot start | Install and run one full build the day before; keep the screenshots as a fallback. |
| No network at the demo | medium | First-time downloads fail | Build once online beforehand; afterwards everything runs offline. |
| Seeded election dates expire before the demo | low | Seeded draft cannot be activated | Reset the demo data on the demo day. |
| Team cannot explain a part of the code | medium | Poor answers to questions | Use `docs/ARCHITECTURE.md` and the traceability table to walk through the voting transaction beforehand. |
| A library update changes behaviour (for example another H2 version) | low | Tests fail after an upgrade | Versions are pinned; the regression suite shows the breakage. |
| Reviewers read the privacy design as a secrecy guarantee | medium | Over-claiming | Quote `docs/SECURITY_AND_LIMITATIONS.md` section 2 in the report. |

## 6. Estimation assumptions to state in the report

- Team of two students; no paid labour. Any monetary labour figure is therefore hypothetical and
  needs a stated hourly rate and source.
- One development laptop; no server costs.
- Scope fixed by the first report; no change requests were processed.
- Maintenance, operation and support after the course are out of scope.
