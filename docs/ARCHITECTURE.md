# Architecture

## 1. Overview

A single Spring Boot application (a layered monolith) serves server-rendered HTML pages.

```
Browser
   |  HTTP (session cookie, CSRF token)
   v
web        controllers, form objects, exception pages   (edu.comp5100.election.web)
security   login, account status filter                 (edu.comp5100.election.security, config)
   |
service    transactions, orchestration, audit           (edu.comp5100.election.service)
   |
policy     pure business rules, no Spring, no database  (edu.comp5100.election.policy)
   |
repository Spring Data JPA interfaces                   (edu.comp5100.election.repository)
domain     JPA entities                                 (edu.comp5100.election.domain)
   |
H2 database, schema managed by Flyway
```

Rules of the layering:

- Controllers and Thymeleaf templates contain no business rules. They call a service and render
  the result or the rejection reason.
- Every decision (may this vote be accepted, may this election be activated, who won) is a pure
  function in the `policy` package. The services gather the facts under a database lock and pass
  them in. This is why the rules can be unit tested and mutation tested without Spring.
- No abstraction exists without a use: there are no interfaces with a single implementation, no
  mappers and no event bus.

## 2. Data model

| Table | Columns | Purpose |
|---|---|---|
| `app_user` | id, username (unique), password_hash, display_name, role, enabled, created_at | Fictional accounts. |
| `election` | id, title, description, starts_at, ends_at, status, created_at | Elections; CHECK `starts_at < ends_at`. |
| `candidate` | id, election_id, name, biography | Candidates; unique (election_id, name) and unique (id, election_id). |
| `election_eligibility` | id, election_id, voter_id, active | Who may vote where; unique (election_id, voter_id). |
| `participation` | id, election_id, voter_id | Who has voted where; unique (election_id, voter_id). No timestamp. |
| `ballot` | id (random UUID), election_id, candidate_id, receipt_hash (unique) | What was chosen. No voter, no timestamp. Foreign key (candidate_id, election_id) to candidate. |
| `audit_event` | id, occurred_at, event_type, actor, election_id, detail | Administrative events and rejected attempts. |

Entities refer to each other by id columns instead of object references. This keeps the mapping
simple (no lazy loading, no cascades) and makes it visible in the code that `Ballot` and
`Participation` share nothing but the election id.

## 3. The voting transaction

`VotingService.castVote` (not transactional) calls `VoteTransaction.castVote` (one transaction):

1. `SELECT ... FOR UPDATE` on the election row. Unknown id: reject.
2. `SELECT ... FOR UPDATE` on the voter's account row.
3. Read the clock. This happens after the locks, so a vote that waited is judged by the time and
   status that are current when it is processed.
4. Gather the facts (account enabled and VOTER, active eligibility, existing participation,
   candidate belongs to this election) and call `VotingPolicy.evaluate`. Any failing rule throws
   `VoteRejectedException`, which rolls the transaction back.
5. Insert the participation row and flush. A unique-constraint violation here is mapped to
   "already voted".
6. Generate the confirmation code, insert the ballot with the SHA-256 hash of the code, flush.
7. Commit. The code is returned to the controller, which passes it as a one-time flash attribute
   through a redirect.

Order of the rule checks, first failure wins: account, eligibility, phase (draft, not started,
ended, closed), duplicate, candidate selected, candidate on this ballot.

If the transaction threw a rejection, `VotingService` then calls
`AuditService.recordRejection`, which runs in its own new transaction. The rejected voting
transaction has already finished at that point, so its rollback cannot remove the audit record.
If writing the audit record fails, the failure is logged and the original rejection is still
thrown.

### Lock order

| Operation | Locks taken, in order |
|---|---|
| Cast a vote | election row, then voter account row |
| Activate, close, edit draft, add or remove candidate, grant or revoke eligibility | election row |
| Enable or disable a voter account | voter account row |

No operation takes the account lock first and the election lock second, so two transactions can
never wait for each other. Lock wait timeout: 10 seconds (`LOCK_TIMEOUT=10000` in the H2 URL).
A timeout surfaces as `VoteNotProcessedException`; the page tells the voter that nothing was
recorded and to check the participation status.

### Ordering contract for closing

Once the closing transaction has committed, no vote is accepted. A vote that took the election
lock before the close is processed first and is accepted; the close waits and then commits. Both
orders are tested with latches in `VotingConcurrencyIT`.

### Cost

All votes of one election are processed one after another. That is acceptable for a classroom
demo (see the load smoke report) and would be the first thing to redesign for a large election.

## 4. Privacy design and its limits

What the design does:

- A ballot row cannot be joined to a voter: there is no voter column, no timestamp, no shared
  request id, and the ballot key is a random UUID, so key order does not reveal insertion order.
- The confirmation code is stored only as a hash. It is displayed once and is not linked to the
  account, so it cannot be recovered later.
- The audit log has no record for an accepted vote and never stores a submitted candidate.
- The confirmation code travels to the page as a flash attribute. It is in the HTTP session for
  the duration of one redirect and is then removed.

What the design does not do (see `docs/SECURITY_AND_LIMITATIONS.md`): it is not anonymous
against someone with database or server access, and it is not a ballot secrecy guarantee.

## 5. Decision records

**ADR-01: Spring Boot 4.1.2 on Java 21.**
Spring's support data (api.spring.io, read on 2026-11-14) lists open-source support for 3.5.x as
ended on 2026-06-30, for 4.0.x until 2026-12-31 and for 4.1.x until 2027-07-31. 4.1.2 was the
newest 4.1 release on Maven Central. Java 21 is a long-term-support release and is supported by
Spring Boot 4.1. The version is pinned in `pom.xml`; all other library versions come from Spring
Boot's dependency management.

**ADR-02: JUnit 6, jqwik 1.9.3 and PIT 1.30.2.**
Spring Boot 4.1.2 manages JUnit Jupiter 6.0.3 (JUnit Platform 6). jqwik 1.9.3 is built against
JUnit Platform 1.13 and the PIT JUnit 5 plugin 1.2.3 predates JUnit 6; neither project documents
support for Platform 6. Both were tried first in a throw-away spike in this project and work:
jqwik properties run under Surefire and Failsafe, and PIT finds and runs the Jupiter tests. jqwik
1.9.3 was chosen on purpose instead of 1.10.x: starting with 1.10 the jqwik author states that the
library is not meant to be used by AI coding agents, and this project was developed with one.
JUnit and Mockito versions are not overridden.

**ADR-03: Flyway owns the schema, Hibernate validates it.**
The constraints that carry the integrity rules (unique participation, composite ballot foreign
key, window check) are written explicitly in `V1__create_schema.sql` and are reproducible on any
empty database. `spring.jpa.hibernate.ddl-auto=validate` makes a mismatch fail at start-up.

**ADR-04: Pessimistic row lock on the election.**
Simple to explain and to test: one `SELECT ... FOR UPDATE` serialises everything that can change
the answer to "may this vote be accepted". The unique constraint stays as the last line of
defence. The alternative (optimistic versioning with retries) was rejected as harder to reason
about for no benefit at demo scale.

**ADR-05: Value checks use `REGEXP_LIKE`.**
With H2 2.4.240, a CHECK constraint of the form `role IN ('ADMIN', 'VOTER')` fails with "database
has been closed" once the connection that created the table is closed. `RepositoryConstraintsIT`
found this; a minimal reproduction outside the application confirmed it. `REGEXP_LIKE` does not
have the problem.

**ADR-06: Confirmation code format.**
160 bits from `SecureRandom`, written as 32 characters of Crockford Base32 in eight groups of
four. Verification normalises case, spaces, dashes and the look-alike letters O, I and L.

**ADR-07: Text fields for date and time.**
Start and end times are typed as `YYYY-MM-DD HH:MM`. A native date-time control would show the
browser's own language and format, which conflicts with the English-only requirement. Forms also
carry `novalidate`, so validation messages always come from the server in English.

**ADR-08: Account status filter in addition to the service check.**
`AccountStatusFilter` ends the session of a disabled account on its next request. The voting
service still checks the account inside its transaction, so the rule does not depend on the
filter.

**ADR-09: Entities are linked by id columns.**
See section 2. It avoids lazy-loading problems with `open-in-view` disabled and keeps the privacy
separation visible.

## 6. Configuration and profiles

| Profile | Database | Purpose |
|---|---|---|
| default | H2 in memory, empty | Start-up check only; there are no accounts. |
| `demo` | H2 file `./data/demo/election-demo` | The demo: seeded once, kept across restarts. |
| `test` (test classpath only) | H2 in memory, one private database per Spring context | Automated tests. |

`election.display-zone` sets the display time zone. The H2 console is disabled in every profile.
