# Local HTTP load smoke test

Run at 2026-11-15T16:15:42-06:00 by `LoadSmokeIT` (`mvnw -Pperformance verify`).

This is a smoke test on one developer machine with client and server in the same JVM. It is not a benchmark, a capacity statement or a service-level claim.

## Environment

- Operating system: Windows 11 10.0 (amd64)
- Logical processors: 8, JVM maximum heap: 4044 MB
- Java: Eclipse Adoptium 21.0.13
- Server: embedded Tomcat on a random local port, H2 in-memory database, connection pool size 30

## Load

- Warm-up (not measured): 12 voters on a separate election, 12 succeeded
- Measured: 60 voters, 12 concurrent client threads, one vote each
- Per voter: GET /login, POST /login (bcrypt), GET ballot, POST vote with CSRF token, GET confirmation
- Then 10 sequential duplicate attempts (expected answer: 409)

## Correctness

| Check | Result |
|---|---|
| Voters whose whole flow succeeded | 60 of 60 |
| Failed flows | 0 |
| Participation records stored | 60 |
| Ballots stored | 60 |
| Duplicate attempts rejected with 409 | 10 of 10 |
| Tally equals the votes that were sent | yes |

| Candidate | Votes sent | Votes stored |
|---|---|---|
| Jordan Lee | 20 | 20 |
| Priya Nair | 20 | 20 |
| Marcus Hill | 20 | 20 |

## Response times of successful requests (milliseconds)

Failed requests, if any, are excluded from this table and listed above instead.

| Request | Count | p50 | p95 | max |
|---|---|---|---|---|
| POST /login (bcrypt check, new session) | 60 | 257.6 | 323.3 | 354.8 |
| GET ballot page | 60 | 17.8 | 45.7 | 59.0 |
| POST vote (accepted, 302) | 60 | 18.4 | 47.2 | 90.7 |
| Whole voter flow (5 requests) | 60 | 432.0 | 505.8 | 565.6 |
| POST vote (duplicate, 409, sequential) | 10 | 9.5 | 31.0 | 31.0 |

Measured phase wall-clock time: 2.20 s for 60 voters (27.2 completed voter flows per second at this concurrency).
