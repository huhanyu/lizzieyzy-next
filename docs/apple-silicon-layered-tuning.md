# Apple Silicon layered KataGo tuning

LizzieYzy Next tunes the bundled Metal KataGo engine for the current Mac and model. A result is not
treated as a universal M1/M2/M3/M4/M5 default: it is tied to the host, KataGo executable, model,
and GTP config by content fingerprints.

## What is tuned

The tuner separates three decisions that the old one-dimensional benchmark combined poorly:

1. Screen a bounded, memory-aware set of Metal neural-network topologies (GPU and ANE lanes), with
   multiple explicit batch sizes for every topology, at a common search-thread count.
2. Run KataGo's official automatic `numSearchThreads` search on every viable topology/batch
   combination. The quick screen is a safety gate only, so a combination is never discarded merely
   because six threads were not its optimum.
3. Repeat the selected configuration in a fresh process. A result with more than 25% throughput
   drift is rejected, and the next finalist is verified instead.

Every candidate uses an explicit `-fixed-batch-size`. This matters because KataGo's benchmark setup
does not use the ordinary `nnMaxBatchSize` value from the GTP config. Mixed GPU/ANE candidates never
use batch size 1 because current Metal/CoreML builds can be unstable with that combination. The
bounded grid tests batches 1/2/4 for one GPU lane and two batch sizes for each multi-lane topology;
there is no universal batch assumption shared across Apple chips or models.

The selected runtime profile contains the exact topology, `nnMaxBatchSize`, and
`numSearchThreads`. Topology or batch changes are applied on the next engine start; they are not
pretended to be complete by sending only a dynamic thread update.

## Scope and invalidation

A profile is used only when all of these still match:

- Mac hardware model, chip/brand, architecture, logical CPU count, unified memory, and macOS build;
- native versus Rosetta execution;
- SHA-256 and size of the KataGo executable, model, and GTP config;
- tuning schema and planner versions.

Paths are intentionally not identities, so moving byte-identical files does not invalidate a good
result. Serial numbers and platform UUIDs are never collected. File hashes are cached by canonical
path, size, and modification time to avoid re-reading a large model on every launch.

The profile is injected only into the main GTP engine. Whole-game analysis, score estimation, and
HumanSL have different concurrency models and keep their existing settings. Explicit command-line
topology, batch, or thread overrides take priority over the corresponding managed settings.

## Failure behavior

Each topology/batch combination runs in an isolated KataGo process. A process registry and startup
gate cover the main engine and every local auxiliary analysis engine; a watchdog continuously checks
that none resumes compute. If isolation is lost, the active benchmark is terminated and no profile
is saved. A crash, non-zero exit, missing completed metrics, or failure to
initialize a requested MPSGraph/CoreML lane removes that candidate without aborting the other
candidates. Cancellation or an inconclusive verification does not overwrite the previous working
profile. The existing analysis lifecycle lease pauses the main engine before tuning and restarts it
afterward, so a newly selected startup-only topology takes effect atomically.

On non-Apple platforms, LizzieYzy Next continues to use KataGo's official thread-count benchmark.
