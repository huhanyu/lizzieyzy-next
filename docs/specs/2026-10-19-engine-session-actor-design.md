# Engine session refactor: one owner per engine

Status: proposed (2026-10-19). Guardrails are in place: see [AGENTS.md](../../AGENTS.md) and
[scripts/architecture_budget.json](../../scripts/architecture_budget.json).

## Problem

Since the fork (2026-03), the engine layer grew much faster than anything else:

| File | Lines at fork | Lines on 2026-10-17 |
|---|---:|---:|
| `analysis/Leelaz.java` | 3,689 | 25,679 |
| `analysis/EngineManager.java` | 2,314 | 15,941 |
| `gui/LizzieFrame.java` | 12,450 | 22,157 |
| `analysis/ReadBoard.java` | 841 | 6,942 |

`Leelaz` alone holds 412 `synchronized` blocks, 115 `volatile` fields and 91 atomics.
`engineArbitrationLock()` is taken in 148 places, and pairs of engines are locked in a fixed order
with a tie-break lock. Main code references the global mutable fields about 7,100 times
(`Lizzie.config`), 3,300 times (`Lizzie.frame`) and 2,000 times (`Lizzie.board`).

Most recent engine bugs are ordering races between several threads that all touch engine state:
stale analysis showing on a new position (#429), analysis resuming after a pause (#406), engine
games stalling after a rules or komi change (#421, #518), batch analysis blocked by its own state
(#523), and ReadBoard candidates disappearing (#560). Each fix added another lease, fence or
admission object, which made the state space larger, not smaller.

## Target design

One **engine session** per engine process owns every piece of engine state and runs on a single
thread. Other threads never lock engine state; they send requests and receive results.

```
UI (event thread) ── requests ──▶ EngineSession (one thread per engine) ──▶ GtpTransport
       ▲                                │  owns: queue, pending replies,                │
       └── AnalysisSnapshot (immutable) ┘  position, rules, komi, analysis, restore  ◀── raw lines
```

1. **`GtpTransport`**: starts and stops the process, SSH session or remote transport. It numbers
   and writes commands and delivers raw stdout/stderr lines into the session's queue. It contains
   no game logic.
2. **Parsers** (pure functions): `kata-analyze`/`lz-analyze` info lines, heat maps, `genmove`
   replies and `list_commands`, each to typed events. They are unit-tested with recorded lines,
   without threads.
3. **`EngineSession`**: a single-threaded state machine (`Starting → Ready → Analyzing → Restoring
   → Stopping`). It owns the command queue, pending responses, the engine-side position, rules and
   komi, and runs restores (`ExactSnapshotEngineRestore`) as session tasks. Generations and epochs
   become plain session-local counters checked in one place, so the lease, fence and admission
   classes go away.
4. **`EngineUiAdapter`**: publishes immutable `AnalysisSnapshot`s to the event thread through a
   coalescer. It holds no engine state, and the session never calls Swing.

Rules for the end state:

- Only the session thread mutates engine state.
- The UI never blocks on an engine.
- Cancellation is a message.
- The analysis package does not reference `Lizzie.frame`.

## Migration (each step is a separate, shippable PR)

1. **Parsers.** Move info-line, heat-map and genmove parsing out of `Leelaz` into
   `featurecat.lizzie.analysis.gtp`, with golden-line tests; `Leelaz` delegates. Tighten the budget.
2. **Transport.** Move process/SSH/remote start, stop and the reader threads into `GtpTransport`,
   which delivers lines to one queue.
3. **Session loop.** Introduce `EngineSession` and route `Leelaz`'s public operations (`sendCommand`,
   `ponder`, restore) through it as messages. `Leelaz` stays as a facade so callers do not change.
4. **Restore as session tasks.** Run exact snapshot restore inside the session and delete the
   admission and lease types it no longer needs. Keep the existing contract tests
   (`ExactSnapshotEngineRestoreContractTest`, `EngineManagerLifecycleReservationTest`) green
   throughout.
5. **Two-engine coordination.** Make engine games and the primary/secondary pair coordinators
   that exchange messages between sessions, and remove the ordered pair locks and the tie-break
   lock.
6. **UI boundary.** Publish analysis only through `EngineUiAdapter` and remove the remaining `gui`
   references from `analysis` (budgeted).

Done when `Leelaz` is a facade of under 5,000 lines, `engineArbitrationLock()` is gone, and the
`analysis → gui` budget is 0.

## Risks and how they are contained

- **Behaviour drift** in restore, rules sync or engine games. Mitigations: each step keeps the
  facade API, the 5,000+ existing tests must stay green, and the steps merge one at a time.
- **Long-running branch.** Avoided because every step stands alone. The budget ratchet stops
  new growth while the steps land.
