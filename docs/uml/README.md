# UML diagrams

| Diagram | Editable source (diagrams.net) | Preview |
|---|---|---|
| Use-case diagram | `01-use-case.drawio` | `01-use-case.svg` |
| Class diagram: domain model | `02-class-domain.drawio` | `02-class-domain.svg` |
| Class diagram: controllers, services, policy rules | `03-class-services.drawio` | `03-class-services.svg` |
| Sequence diagram: a vote is accepted | `04-sequence-vote-accepted.drawio` | `04-sequence-vote-accepted.svg` |
| Sequence diagram: a duplicate vote is rejected | `05-sequence-vote-rejected.drawio` | `05-sequence-vote-rejected.svg` |
| Deployment diagram: local demo | `06-deployment.drawio` | `06-deployment.svg` |
| State diagram: election life cycle | `07-state-election.drawio` | `07-state-election.svg` |

## How these files were made, and what was not done

- Each diagram is described once, as a short text file in `src/` (`*.uml`).
- `src/UmlGenerator.java` reads a description and writes **both** the `.drawio` file (the
  diagrams.net XML format) and the `.svg` preview. Both therefore always show the same diagram.
  Regenerate with: `java docs/uml/src/UmlGenerator.java` (from the project root, JDK 21).
- The SVG files were drawn by this generator. They are **not** exports made by diagrams.net.
  The SVG previews were rendered in Chrome and looked at once; the `.drawio` files were checked
  to be well-formed XML but **have not been opened in diagrams.net** during development.
- To satisfy the course requirement "use a free UML tool", open the `.drawio` files in
  diagrams.net (<https://app.diagrams.net> or the desktop application), adjust the layout if you
  wish, and export PNG or SVG from there for the report.

## Consistency with the code

- Class names, attributes and operations are those of the Java classes in
  `src/main/java/edu/comp5100/election`. Entities are linked by id attributes, as in the code;
  the arrows in the domain diagram show those id references.
- The sequence diagrams follow `VotingService.castVote` and `VoteTransaction.castVote` step by
  step: election lock, account lock, clock, facts, policy decision, participation insert, code
  generation, ballot insert, commit; and for a rejection: rollback first, then the audit record
  in a second transaction.
- The state diagram shows the stored status. The phases "Not started", "Voting open" and
  "Voting ended" are derived from the clock while the status is OPEN; they are described in the
  note, not drawn as stored states.

If the code changes, change the `.uml` description and regenerate.
