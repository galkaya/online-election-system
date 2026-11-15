# Results of the experiment

Measured on 2026-11-15 by `scripts/run-ai-experiment.ps1` at commit `b0abee9` with a clean
working tree. The generated table is `docs/evidence/ai-experiment/summary.md`; the raw JaCoCo
and PIT files of both runs are in the same folder.

## Set-up

| Item | Baseline run | Expanded run |
|---|---|---|
| Production code | commit `b0abee9` | the same commit |
| Tests | everything without the tag `adversarial` (`-Pbaseline`) | everything |
| Coverage tool and settings | JaCoCo 0.8.16, Surefire and Failsafe merged | the same |
| Mutation tool and settings | PIT 1.30.2, `policy.*`, DEFAULTS, 2 threads | the same |
| Tests visible to PIT | `unit.*` (10 classes) | `unit.*` and `adversarial.unit.*` (12 classes) |
| Mutants generated | 99 | the same 99 |
| Random seeds | PIT generated the same 99 mutants in every run. jqwik used fresh random seeds in each run (a failing seed would be stored in `.jqwik-database`; there was none) | the same |

The production code did not change between the freeze of the baseline (tag `baseline-suite`,
commit `14f8d39`) and the measured commit in any class of the `policy`, `service`, `web`,
`security` or `domain` packages. A few lines of demo seed data changed, in `DemoDataSeeder` (both seeded referendum votes now go to the same option).
Both suites were measured on the final code, in the same script run, one after the other.

## Measured

| Measure | Baseline | Expanded | Difference |
|---|---|---|---|
| Tests in the default build | 238 | 286 | +48 |
| Line coverage, whole application | 876/921 (95.1%) | 876/921 (95.1%) | none |
| Branch coverage, whole application | 261/290 (90.0%) | 261/290 (90.0%) | none |
| Line / branch coverage, `policy` | 98.8% / 99.1% | 98.8% / 99.1% | none |
| Mutants killed | 92 of 99 (92.9%) | 96 of 99 (97.0%) | +4 |
| Mutants timed out | 1 | 1 | none |
| Mutants survived | 4 | 2 | -2 |
| Mutants without coverage | 2 | 0 | -2 |
| `verify` wall-clock time | 55 s | 59 s | +4 s |
| PIT wall-clock time | 29 s | 32 s | +3 s |

Wall-clock times vary by a few seconds between runs on this laptop (three recorded runs gave
55 to 60 s for the baseline `verify` and 59 to 64 s for the expanded one).

## The four mutants that only the expanded suite kills

| Mutant | Baseline status | Killed by |
|---|---|---|
| `VoteRejectionReason.message()` returns "" | survived | `MessageCatalogTest` (C23) |
| `ActivationProblem.message()` returns "" | no coverage | `MessageCatalogTest` (C23) |
| `ElectionPhase.label()` returns "" | no coverage | `MessageCatalogTest` (C23) |
| `TallyCalculator.percent`: zero-total guard removed | survived | `BoundaryAndGuardTest#percentWithoutTotalIsZero` (C26) |

The two "no coverage" mutants need a caveat: the texts they blank are checked by the baseline
MockMvc and browser tests. PIT cannot see those tests, because it runs only the unit-level
suite. So for these two, the gain is "now also caught at unit level", not "now caught at all".

## The mutants that neither suite kills

| Mutant | Status | Assessment |
|---|---|---|
| `ReceiptCodec.encode`: loop decrement changed to an increment | timed out in both suites | Creates an endless loop. Detected only by PIT's timeout. |
| `ReceiptCodec.encode`: shift direction in the buffer mask | survived | Equivalent: the masked bits are never read. Candidate C24 (dense bit patterns) was written against this mutant and, as expected for an equivalent mutant, does not kill it. |
| `ReceiptCodec.normalize`: early exit for over-long input removed | survived | Equivalent: the final length check rejects the same inputs. |

## What the experiment shows

1. **No new defect.** All 14 accepted candidates passed on their first run against the unchanged
   production code. The adversarial pass found nothing wrong with the application.
2. **No coverage gain.** Line and branch coverage are identical. The added tests exercise code
   that the baseline already executed.
3. **A small mutation gain.** Four more mutants are killed (92 to 96 of 99). Three of the four
   concern message texts, one a defensive guard. None concerns the voting rules, the window
   boundaries or the tally itself: the baseline already killed every mutant there.
4. **Behaviour checked for the first time**, although no metric above shows it, because it lies
   outside the mutation scope and adds no coverage: a vote that waits for the lock past the
   deadline (C05); simultaneous duplicates through real HTTP sessions (C02); the content of the
   log (C15); identical verification answers for different ballots (C17); a CSRF token from
   another session (C27); a new session id at login (C28); hostile id values (C08); reflection
   of the verification input (C35); re-enabling an account (C14).
5. **Cost.** 48 more tests, about 4 seconds more build time, about 3 seconds more PIT time.

## What it does not show

- It is one run on one small application. It is not a study, and no general statement about
  AI-assisted test design follows from it.
- The baseline and the additions were written by the same AI assistant, which had seen the code,
  the baseline tests and the baseline PIT report. The baseline was thorough for the same reason
  the additions were modest: 17 of 36 candidates were already covered. A weaker baseline would
  have produced a larger and less meaningful difference.
- Two of the candidates (C23, C26) were derived directly from the baseline PIT report. Killing
  the mutants they were written for is expected, not a discovery.
- The mutation scope is the `policy` package and the unit-level tests. The numbers say nothing
  about the services, the web layer or the database rules, where most of the added tests are.
- No person has reviewed the candidate decisions yet.

## Kinds of faults, kept apart

| Kind | Count in this experiment |
|---|---|
| Real defects found by the adversarial tests | 0 |
| Real defects found by the baseline tests during development | 1 (the H2 CHECK constraint; see `docs/TEST_RESULTS.md`, section 8) |
| PIT mutants additionally killed by the expanded suite | 4 |
| Artificially seeded faults (hand-written faults injected to try the tests) | 0; none were injected |
