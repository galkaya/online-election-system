# AI-assisted test design experiment

The application itself has no AI component. This folder records one experiment about the test
suite: **can an AI-assisted pass over the requirements and threat scenarios add negative and
boundary tests that make the suite measurably stronger?**

| File | Content |
|---|---|
| `01-prompt.md` | The instruction the assistant worked from, the date, the model and the conditions. |
| `02-raw-candidates.md` | The candidate tests as they were proposed, before any decision. |
| `03-candidate-decisions.md` | For each candidate: requirement, expected result, why it matters, whether the baseline already covered it, accept or reject, reason, and review status. |
| `04-results.md` | Baseline against expanded suite on the same code and settings: measured numbers and what they do and do not show. |
| `../evidence/ai-experiment/` | Raw JaCoCo and PIT reports of both runs and the generated `summary.md`. |

Terms used here:

- **Baseline suite**: every test without the JUnit tag `adversarial`. Frozen at Git tag
  `baseline-suite`.
- **Expanded suite**: the baseline plus the tests tagged `adversarial`
  (`src/test/java/edu/comp5100/election/adversarial`).

Both suites were written by the same AI assistant. This is **not** a comparison of human-written
and AI-written tests.

To repeat the measurement: `.\scripts\run-ai-experiment.ps1`.
