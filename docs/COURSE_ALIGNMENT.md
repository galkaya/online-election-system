# Course alignment

How the project relates to the topics of COMP 5100 Software Engineering. This file lists
evidence that exists in the repository. It does not describe sprints, meetings or a division of
work, because none were recorded.

| Course topic | What exists in this project | Where |
|---|---|---|
| Introduction: problem and scope | Problem statement, simulation scope and explicit non-goals (no real elections, no cryptographic verification). | `README.md`, `docs/REQUIREMENTS.md`, `docs/SECURITY_AND_LIMITATIONS.md` |
| Software processes | Incremental process actually followed: environment check and tool spike, schema and domain, rules and services, user interface, tests level by level, mutation analysis, adversarial test pass, documentation. Each step ended with a passing build. | `docs/IMPLEMENTATION_STATUS.md`, Git history |
| Agile development | Requirements written as small items with acceptance criteria that map one-to-one to automated tests; working software was demonstrated after each increment by running it. No sprint ceremonies were held or recorded. | `docs/REQUIREMENTS.md`, `docs/TRACEABILITY.md` |
| Requirements engineering | Actors, 18 functional and 11 non-functional requirements with identifiers and acceptance criteria; traceability from each requirement to code, tests and demo steps. | `docs/REQUIREMENTS.md`, `docs/TRACEABILITY.md` |
| System modeling (UML) | Use-case, class (domain and services), sequence (accepted and rejected vote), state and deployment diagrams as editable diagrams.net files. | `docs/uml/` |
| Architectural design | Layered monolith, data model, transaction boundary, lock order, privacy trade-offs and nine short decision records. | `docs/ARCHITECTURE.md` |
| Design and implementation | Java 21, Spring Boot, Spring Security, Thymeleaf, H2, Maven; business rules isolated in a pure `policy` package. | `src/main/java`, `src/main/resources` |
| Software testing | Unit, component/integration and browser system tests with JUnit; security, concurrency, property-based (jqwik), mutation (PIT) and a load smoke test; a recorded baseline-versus-expanded experiment. | `src/test/java`, `docs/TESTING.md`, `docs/TEST_RESULTS.md`, `docs/ai-testing/` |
| Software evolution | Versioned schema migrations (Flyway); the full regression suite runs with one command; a defect found by a test, its fix and its regression test are documented. | `src/main/resources/db/migration`, `docs/TEST_RESULTS.md` (section "Defects found") |
| Project planning | Remaining work, dependencies, risks and the assumptions behind any estimate, with measured and estimated values kept apart. | `docs/PLANNING_INPUTS.md`, `docs/IMPLEMENTATION_STATUS.md` |
| Dependability and security | Threat table with mitigations and test evidence, and a list of what is not guaranteed. | `docs/SECURITY_AND_LIMITATIONS.md` |

## Items of the course project description

| Item of the description | State in this repository |
|---|---|
| Implementation in Java | Done: `src/main/java`. |
| JUnit testing | Done: JUnit Jupiter tests at three levels plus additional suites. |
| UML with a free tool | Editable diagrams.net (draw.io) sources in `docs/uml/`. |
| Running demo and JUnit shown live | Prepared: `docs/DEMO_SCRIPT.md`. The live demo itself has not happened yet. |
| Final report (summary, goals, requirements, modeling, testing, cost analysis, conclusion, appendix, references) | **Not written.** This repository provides inputs for it: requirements, architecture, UML sources, test results, screenshots, planning inputs and references. |
| Presentation | **Not prepared.** |
| Single ZIP submission through ELEARN | **Not done.** |
