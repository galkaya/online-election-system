# References

## 1. Course documents

- COMP 5100 Software Engineering, "Project Description" (`ProjectDescription1.pdf`). Read in
  full; source of the required deliverables.
- G. Alkaya and D. Martin, "Online Election System", first report, October 29, 2026. Read in
  full; source of the accepted scope, tools and demo idea. Its own reference list (Estonian
  i-voting, Helios, ElectionGuard, the CISA/EAC/FBI/NIST risk paper, OWASP ASVS, Spring Boot
  testing reference) was not re-read during implementation.

## 2. Official documentation and data consulted during implementation

Consulted on 2026-11-14 and 2026-11-15.

| Source | Used for |
|---|---|
| Spring project support data, <https://api.spring.io/projects/spring-boot/generations> | Choosing a supported Spring Boot line (ADR-01). |
| Maven Central metadata for `spring-boot-starter-parent`, `spring-boot-dependencies` 4.1.2, `jqwik`, `pitest-maven`, `pitest-junit5-plugin`, `jacoco-maven-plugin`, `apache-maven`, `maven-wrapper-distribution` (<https://repo.maven.apache.org/maven2/>) | Exact versions; the JUnit, Selenium, H2 and Hibernate versions managed by Spring Boot 4.1.2. |
| Eclipse Adoptium API, <https://api.adoptium.net/> | Temurin JDK 21.0.13 download location and SHA-256 checksum. |
| jqwik release notes and user guide, <https://jqwik.net/release-notes.html>, <https://jqwik.net/docs/current/user-guide.html> | JUnit Platform requirements; the author's statement about use with AI coding agents from version 1.10 (ADR-02). |
| PIT JUnit 5 plugin README, <https://github.com/pitest/pitest-junit5-plugin> | Plugin and PIT version pairing. |

Listed in the task description as starting points and relied on through prior knowledge of the
tools rather than re-read page by page: Spring Boot testing how-to, Spring Security CSRF
reference, PIT Maven quick start, jqwik user guide chapters other than those above. API details
that differ in Spring Boot 4 (new test starter modules and package names) were confirmed against
the downloaded library jars and by compiling.

## 3. Third-party code and assets

- **Maven Wrapper** scripts `mvnw` and `mvnw.cmd`: copied unchanged from the Apache Maven Wrapper
  distribution 3.3.4 ("only-script"), Apache License 2.0.
- **Libraries**: used as Maven dependencies, not copied into the repository. Versions are in
  `pom.xml` and in Spring Boot's dependency management.
- **Fonts, icons, images, CSS or JavaScript frameworks**: none. The stylesheet, the small logo
  and the favicon were written for this project. The pages load nothing from other servers.
- **Code snippets from tutorials, forums or other projects**: none were copied.
- **Test vector**: the SHA-256 digest of the text "abc" used in `ReceiptCodecTest` is the
  well-known example from the Secure Hash Standard (FIPS 180).
- **Crockford Base32** alphabet (digits and letters without I, L, O, U): the encoding described
  by Douglas Crockford; implemented from its description.

## 4. Use of AI tools

This must be disclosed in the final report.

- The source code, tests, scripts, UML sources and the documents in `docs/` were written by an AI
  coding assistant: **Claude Code (Anthropic), model Claude Fable 5.2 (`claude-fable-5-2`)**, in
  sessions on 2026-11-14 and 2026-11-15, following a written task description prepared by the
  team and the two course documents above.
- The assistant also ran the builds and tests; the results in `docs/TEST_RESULTS.md` are the
  recorded output of those runs on the development laptop.
- Both the baseline test suite and the additional "adversarial" tests were written by the same
  assistant. The experiment in `docs/ai-testing/` therefore compares a baseline suite with an
  AI-assisted additional suite; it does not compare human-written with AI-written tests.
- Review by the team members is still pending for the items marked so in
  `docs/ai-testing/03-candidate-decisions.md`. Nothing in this repository was reviewed by an
  instructor.
- The running application contains no AI component and calls no AI service.
- Usage cost of the assistant: not known.
