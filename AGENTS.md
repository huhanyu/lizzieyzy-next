# Agent guide for LizzieYzy Next

This file is for coding agents (Codex, Claude Code, Cursor, …) and for people reviewing their
changes. It records the rules that are easy to miss in a 300k-line Swing code base. Product and
user-facing conventions are in [CONTRIBUTING.md](CONTRIBUTING.md); build details are in
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Build and verify

- Full gate, as CI runs it: `python3 scripts/run_local_ci.py --profile portable --group java`
  (Windows: `--profile windows`). Quick loop: `mvn -B -q -Dfmt.skip=true -Djava.awt.headless=true
  -Dtest='SomeTest' -Dsurefire.failIfNoSpecifiedTests=false test`.
- Pass `-Dlizzie.work.dir=<temp dir>` when running tests locally so nothing is written to your real
  work directory.
- Repository checks: `python3 scripts/check_line_endings.py`,
  `python3 scripts/check_architecture_budget.py` and
  `python3 scripts/list_untranslated_strings.py --check`.
- Java code is google-java-format clean and CI runs `fmt:check`: run
  `mvn -B -q com.spotify.fmt:fmt-maven-plugin:format` before committing. Text files are LF (only
  `.bat`/`.cmd` are CRLF).

## Architecture rules

The engine layer and main window grew into a few very large classes guarded by many locks. The
refactor plan is in [docs/specs/2026-10-19-engine-session-actor-design.md](docs/specs/2026-10-19-engine-session-actor-design.md).
Until it lands, every change must keep these numbers from growing:

1. **Do not grow the hot spots.** `Leelaz`, `LizzieFrame`, `EngineManager`, `Menu` and the other
   files listed in [scripts/architecture_budget.json](scripts/architecture_budget.json) have line
   budgets enforced in CI. Put new behaviour in a new, focused class with its own tests and call it
   from the hot spot. If you shrink a listed file, run
   `python3 scripts/check_architecture_budget.py --tighten` and commit the lower budget. Pure
   parsers for engine output belong in `featurecat.lizzie.analysis.gtp` (for example
   `KataRawNnResponse`), tested with recorded engine output.
2. **No new global mutable state.** Do not add `public static` mutable fields, and do not add new
   references to `Lizzie.config`, `Lizzie.frame`, `Lizzie.board`, `Lizzie.leelaz` or
   `Lizzie.engineManager` in new code: pass what the class needs through its constructor. The
   reference counts are budgeted too.
3. **Layering.** `analysis`, `rules` and `update` must not reach into `gui` (budgeted). Engine code
   reports state; UI code decides how to show it.
4. **Threads.** Swing components, `BoardData` comments and other UI state are touched on the event
   thread only. Engine reader threads hand results over (for example
   `SwingUtilities.invokeLater`, or a coalescer such as `analysisRefreshCoalescer`). Do not add a
   new lock, lease, fence or "admission" object to fix a race without first writing down which
   single owner should hold that state; prefer one owner thread per engine.
5. **Fail closed, but explain.** Engine restore failures must not leave the user waiting: stop
   retries, keep the user's game, and show the reason (see `EngineFollowController`).

## Security rules

- Never write passwords, tokens or keys into the config file. Use
  `featurecat.lizzie.analysis.remote.PlatformCredentialStore` (Keychain / DPAPI / Secret Service);
  SSH engine passwords go through `SshEnginePasswords`.
- `Utils.doEncrypt`/`doDecrypt` use a key that ships in the source: they are obfuscation, not
  encryption. Do not use them for new data.
- SSH goes through `featurecat.lizzie.analysis.ssh.SshCommandSession`, which verifies host keys
  against `ssh/known_hosts`. Do not open sockets that send credentials without TLS or host-key
  verification.
- Downloads that end up executed or loaded (engines, runtimes, updates) are pinned by SHA-256, and
  update manifests are Ed25519-signed (`SignedUpdateEnvelope`). Keep it that way.
- The embedded browser only trusts Yike bridge messages from the main frame on yikeweiqi.com
  (`BrowserFrame.isTrustedYikeQuerySource`). New bridges need the same kind of origin check.

## Logging

- Log through SLF4J (`LoggerFactory.getLogger(...)`) so failures reach `logs/app.log` and the
  diagnostic bundle. Do not add new `printStackTrace()` calls; the stderr copy into app.log
  (`StandardErrorCapture`) is only a safety net for old code.

## Tests

- Every behaviour change ships with a test that fails without it.
- Do not wait for real wall-clock timeouts in tests. Make the timeout injectable (a `Clock`,
  scheduler or package-private duration) and use a short value; sleeping tests are the main source
  of slow and flaky CI.
- Tests run in one JVM per fork with shared statics: restore any `Lizzie.*` field you change, and
  drain event-thread work you start.
- Frames in tests are often created without running constructors; avoid instance field
  initializers that later code dereferences.

## Localization

- User-visible text lives in `src/main/resources/l10n/DisplayStrings*.properties` (8 bundles). Add
  every key to all bundles at the same position with the same placeholders;
  `LocalizationResourceParityTest` enforces it. `MessageFormat` patterns must not contain a bare
  apostrophe.
- Translate new strings in the same change: `python3 scripts/list_untranslated_strings.py --check`
  fails when a bundle gains English text (brand and protocol names are allow-listed in the script).

## Pull requests

- One topic per PR, conventional titles (`fix(ssh): …`, `feat(rules): …`), an entry under
  `## Unreleased` in [CHANGELOG.md](CHANGELOG.md) for user-visible changes, and the PR template
  filled in.
- Do not touch release metadata (`.github/release-requests`, `.github/release-notes`, versions)
  unless the change is a release.
