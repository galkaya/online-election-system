# Security notes and limitations

This is an academic prototype for a classroom simulation. This document states what is
protected, how, and what is **not** guaranteed.

## 1. Assets and threats considered

| Asset | Threat | Mitigation | Evidence |
|---|---|---|---|
| One vote per voter | Double submission, parallel sessions, replayed form | Election row lock, re-check under lock, unique constraint | `VotingConcurrencyIT`, `AdversarialHttpIT`, `RepositoryConstraintsIT` |
| Integrity of the tally | Half-written vote, vote for a foreign candidate, drifting counters | Single transaction, composite foreign key, tally computed from ballots | `VoteRollbackIT`, `VotingServiceIT`, `TallyProperties` |
| Voting window | Late vote that waited in a queue, client clock tricks | Server clock read after the lock | `AdversarialVotingIT`, `VotingWindowBrowserIT` |
| Administrator functions | Voter calls administrator URLs, role parameter tampering | Server-side role checks on every URL | `WebLayerIT`, `SecurityRegressionIT` |
| Accounts | Disabled account with an open session | Account status filter and service check | `SecurityRegressionIT`, `VotingConcurrencyIT` |
| Sessions and forms | Cross-site request forgery, session fixation | Spring Security CSRF tokens, new session id at login, HttpOnly SameSite cookie | `WebLayerIT`, `HttpSecurityIT`, `AdversarialHttpIT` |
| Pages | Script or SQL typed into names and fields | Thymeleaf escaping, parameterised queries | `SecurityRegressionIT`, `AdversarialWebIT` |
| Link between voter and choice | Reading it from the application, pages, audit or logs | No such link is stored or logged | `RepositoryConstraintsIT`, `SecurityRegressionIT`, `LogPrivacyIT` |
| Interim results | Reading totals while voting is open | Results service refuses until the election has ended | `SecurityRegressionIT`, `ElectionAdminServiceIT` |
| Error pages | Stack traces, framework messages | Custom error page, details disabled | `HttpSecurityIT` |

The tests named above are a limited regression pack. They are **not** a penetration test and
not a security certification.

## 2. What is not guaranteed

**Ballot secrecy is not guaranteed.** The separation of participation and ballot records makes
it impossible to read a voter's choice through the application. It does not make the system
anonymous:

- A person with access to the database files, the H2 transaction log or the server memory can
  observe the order and time of writes and can correlate a participation row with a ballot row.
- Participation and ballot are written in the same transaction, one right after the other. Row
  storage order inside H2 may reflect that order even though the ballot key is random.
- The confirmation code stays in the HTTP session, as a flash attribute, between the vote and the
  next page view.
- With a single voter, or when all voters choose the same candidate, the published result
  reveals the choice. Turnout counts shown to the administrator during voting add to this.
- Network observers and the administrator of the machine see who connects and when. The demo uses
  plain HTTP on localhost.
- Coercion, vote selling and malware on the voter's device are outside what any part of this
  prototype addresses.

**Verifiability is limited.** A confirmation code proves only that a ballot with that code is
stored. It does not prove that the ballot names the candidate the voter chose, and it does not
let anybody verify the tally independently. There is no cryptographic end-to-end verification.

**Identity is fictional.** Accounts are created by the administrator with local passwords.
There is no identity proofing, no multi-factor authentication, no password recovery, no account
lockout and no rate limiting.

**The administrator is trusted.** An administrator can create accounts, make them eligible, and
close an election early. The audit page records these actions, but the audit table is an ordinary
database table: it is not tamper-evident.

**Lost response.** If the response with the confirmation code is lost (for example the browser
closes), the vote is still stored and the code cannot be shown again. The system never
automatically re-submits a vote. The voter can see on "My elections" that their participation is
recorded. This is a known limitation of the prototype.

**Concurrency evidence is specific to H2.** The lock and race tests show the behaviour with H2
2.4 row locks at the default READ COMMITTED isolation level. They do not prove the same behaviour
on another database engine.

**Operational aspects are absent.** No TLS configuration, backup, monitoring, log retention
policy, secret management or deployment hardening is part of this project.

## 3. Settings worth knowing

- CSRF protection: Spring Security default, enabled for every POST.
- Password hashing: Spring Security delegating encoder with bcrypt.
- Session cookie: HttpOnly, SameSite=Lax, cookie-only session tracking, 30 minute timeout.
- Caching: Spring Security sends no-store cache headers; the confirmation and verification pages
  are never cacheable.
- H2 console: disabled.
- Error pages: no stack trace, exception name or framework message.
- Logging: the application writes no password, confirmation code, session id or candidate choice.
  `LogPrivacyIT` checks this at a more verbose log level than the shipped configuration.

## 4. Demo credentials

The accounts listed in `README.md` exist only in the local demo database and are fictional. Do
not reuse those passwords anywhere.
