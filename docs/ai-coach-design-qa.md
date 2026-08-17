# AI Coach Design QA

## Visual Target

- Selected direction: `Option 2 - Professional Training Desk`
- Reference states: top toolbar, training setup, live correction, and post-game report
- Implementation: native Swing components with the existing LizzieYzy Next board and toolbar

## Verified States

- Setup dialog at `920 x 410`: five primary choices remain visible without scrolling, rank selection supports `20 kyu - 9 dan`, and the HumanSL download state stays inline.
- Live training at `1065 x 700`: `AI Coach` appears immediately before `AI Commentary`, the correction card stays non-modal, and the integrated training bar preserves the board workspace.
- Training report at `1040 x 590`: all three critical positions, values, board previews, and actions are visible without horizontal scrolling.
- Physical font fallback renders CJK text, Latin labels, percentages, move numbers, and decimal values without missing glyphs.
- Localized opponent rank is user-facing (`3段`, `12 kyu`) rather than an internal profile token (`3D`, `12K`).

## Interaction And Accessibility

- The top entry mirrors the live session state and rejects duplicate session starts.
- Setup controls expose accessible names for training mode, opponent type, rank, color, time, download controls, and the primary action.
- Keyboard focus is available for setup choices, correction actions, training controls, and report actions.
- Closing setup during preparation invalidates the pending start instead of creating a hidden game later.
- Live correction and report actions are non-destructive until the user explicitly chooses to retry, end, or save.

## Comparison Findings

- Toolbar grouping, teal primary actions, warm neutral surfaces, compact spacing, and report hierarchy match the selected reference direction.
- The implementation deliberately keeps the real LizzieYzy Next board and existing application chrome instead of replacing them with static artwork.
- The setup dialog is more compact than the reference download-progress state while retaining the same information hierarchy.
- macOS rendering was inspected from actual Swing windows. Windows high-DPI rendering remains a release-gate validation item rather than an assumed pass.

final result: passed
