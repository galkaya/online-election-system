# Prompt and conditions

## Conditions

| Item | Value |
|---|---|
| Date | 2026-11-15 |
| Tool and model | Claude Code (Anthropic), model Claude Fable 5.2 (`claude-fable-5-2`) |
| How the prompt was used | The text below is the working instruction the assistant followed inside the same development session. It was **not** sent as a separate request to a fresh model instance. |
| What the model could see | Everything in the session: the requirements, the production code, the complete baseline test suite and the baseline PIT report. The suggestions are therefore not independent of the baseline. |
| Baseline frozen before the suggestions | Yes: Git tag `baseline-suite`, commit `c1849be`. |
| Token usage and cost | Not available in the session; not recorded. |
| Reviewed by | The same assistant (accept/reject decisions and implementation). Review by the team members is pending. |

A stricter variant of this experiment would send the same prompt to a model instance that has
never seen the code or the tests. That was not done. The prompt is kept here so that it can be.

## Prompt

> You are reviewing the test plan of an online election simulation. Do not look for ways to
> raise coverage numbers. Derive negative and boundary test candidates from the requirements and
> from the threat scenarios below, and state the expected result of each from the requirement,
> not from what the code currently does.
>
> Requirements (summary of `docs/REQUIREMENTS.md`): session login with ADMIN and VOTER roles;
> per-election eligibility; life cycle DRAFT, OPEN, CLOSED; votes accepted only when
> `startsAt <= now < endsAt` by the server clock; one candidate per vote; one vote per voter and
> election, enforced by a database constraint and a single transaction; every rejection has a
> clear English reason and stores nothing; a random confirmation code is shown once and can be
> checked later, revealing only "accepted" or not; results only after the election has ended;
> an audit page with administrative events and rejected attempts but never a choice or a code;
> ballots hold no voter identity.
>
> Threat scenarios to cover explicitly:
> 1. simultaneous duplicate votes;
> 2. the exact end instant of the voting window;
> 3. a candidate id that belongs to another election;
> 4. rollback when one of the two writes of a vote fails;
> 5. a vote racing with the closing of the election;
> 6. a session that was opened before the account was disabled;
> 7. leaks of the confirmation code or of the choice through pages, the audit log, logs, URLs or
>    the verification answer.
>
> For each candidate give: an identifier, the requirement it belongs to, the scenario, the
> expected result, and one sentence on why it matters. Include candidates even if you suspect the
> existing tests already cover them; coverage by the baseline is decided in a second step. Also
> look at the surviving and uncovered mutants of the baseline PIT report and propose a candidate
> for each one that points at a real gap; say so when a surviving mutant is equivalent.
