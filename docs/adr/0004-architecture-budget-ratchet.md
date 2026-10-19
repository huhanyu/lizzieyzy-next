# ADR 0004: Architecture budgets only go down

- Status: accepted (2026-10-19)
- Context: [engine session refactor](../specs/2026-10-19-engine-session-actor-design.md)

## Context

Between March and October 2026 the main code grew from about 117k to 318k lines, and the largest
classes grew fastest (`Leelaz` 7×, `EngineManager` 7×). Much of the code is written by coding agents.
They tend to fix a symptom where it shows up, which usually means adding to the class that is
already largest. Review alone has not stopped this.

## Decision

`scripts/check_architecture_budget.py` runs in CI's repository checks against
`scripts/architecture_budget.json`:

- line budgets for every main Java file over 2,000 lines, and a 2,000-line limit for every other
  file;
- budgets for references to the global mutable `Lizzie.*` fields in main code;
- budgets for `analysis`, `rules` and `update` code referring to `gui` classes.

A value may never exceed its budget. After a change shrinks code,
`python3 scripts/check_architecture_budget.py --tighten` lowers the budget to the new value.
Raising a budget is an explicit edit, visible in review, and needs a reason.

## Consequences

- A change that needs more code in a hot spot must first move other code out, or justify a
  budget increase.
- Progress of the refactor can be read straight from the budget file's history.
- The check counts lines and references, not design quality. It is a guardrail that keeps the
  refactor from losing ground, not a goal in itself.
