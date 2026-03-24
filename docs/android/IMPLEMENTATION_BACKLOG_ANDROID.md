# Android Implementation Backlog

This backlog is ordered to maximize usable progress while preserving parity discipline.

## iOS Reference Baseline

Primary iOS reference repo:
- https://github.com/riefkyhd/samsung-remote-tv-ios
- Branch: `main`

Default behavioral baseline:
- use the latest accepted iOS stabilization state unless a task names a more specific commit

Recommended known reference points:
- `87b91f4` — final v1 stabilization sweep baseline
- `853fff0` — later accepted iOS state including JU/UX follow-up work

For every backlog item:
- check the equivalent iOS behavior first
- preserve product truthfulness
- preserve support-boundary wording
- explain any intentional Android divergence

---

## Rules for Executing This Backlog

- one narrow phase at a time
- inspect first
- explain files / risks / validation before editing
- validate after each phase
- keep docs aligned with behavior
- do not introduce new features unless explicitly scoped
- do not regress iOS truthfulness or lifecycle clarity

---

## A-01 — Android project shell

### Goal
Create the Android app foundation.

### Deliverables
- app module ready
- package structure
- Gradle setup
- Compose setup
- Hilt setup
- navigation setup

### iOS parity target
Mirror the iOS app’s high-level structure:
- `data`
- `domain`
- `presentation`
- `utilities/core`

### Acceptance
- app launches
- navigation works between placeholder screens
- no architecture shortcuts
- project structure clearly supports future parity work

### Status (2026-03-25)
- In Progress
- Compose + Hilt + Navigation shell added in Android repo
- `core` / `data` / `domain` / `presentation` package structure scaffolded
- Local validation executed: `assembleDebug`, `testDebugUnitTest`, and `lintDebug`

---

## A-02 — Design token baseline

### Goal
Create Android design primitives that mirror iOS feel.

### Deliverables
- colors
- spacing
- radii
- icon sizes
- typography mapping
- reusable button styles

### iOS parity target
Match Discovery / Remote / Settings hierarchy and control prominence as closely as practical.

### Acceptance
- key screens can render using shared design tokens
- Android does not drift into generic Material defaults accidentally
- tokens are reusable across all screens

### Status (2026-03-25)
- In Progress
- token baseline added: spacing, radii, icon sizes, semantic colors, typography, reusable action buttons
- tokens now drive placeholder Discovery / Remote / Settings screens
- visual parity still pending feature-complete screen behavior

---

## B-01 — Domain model parity

### Goal
Create Android domain models matching iOS behavior.

### Deliverables
- `SamsungTv`
- connection states
- capabilities model
- remote key model
- quick launch model

### iOS parity target
Mirror the iOS domain concepts and naming closely enough that parity work remains traceable.

### Acceptance
- domain layer is stable and platform-clean
- parity terms match iOS semantics
- capability truthfulness is preserved

---

## B-02 — Use case layer scaffold

### Goal
Port the core use case structure first.

### Deliverables
- connect
- disconnect
- send remote key
- launch quick launch app
- forget pairing
- remove device
- rename TV

### iOS parity target
Presentation must remain use-case driven, just like the stabilized iOS architecture.

### Acceptance
- use cases exist before screen logic expands
- presentation can depend on them cleanly
- no direct Presentation -> concrete repository regressions

---

## B-03 — Modern transport baseline

### Goal
Implement modern TV connect/control first.

### Deliverables
- modern transport
- connect/disconnect
- send key
- basic state flow
- error handling

### iOS parity target
Match the stabilized modern-TV path first before attempting legacy encrypted parity.

### Acceptance
- modern TV core controls work
- state labels are truthful
- saved-device reconnect works
- no false “ready” implication

---

## C-01 — Discovery screen parity

### Goal
Match iOS discovery screen flow.

### Deliverables
- discovery state
- list rendering
- empty state
- saved TVs section
- discovered TVs section

### iOS parity target
Maintain iOS-style clarity between:
- saved TVs
- discovered TVs
- manual add flow
- empty/network-dependent states

### Acceptance
- UI flow is close to iOS
- row actions are clear
- no confusing duplicate entries
- support-boundary wording remains truthful

---

## C-02 — Manual IP flow parity

### Goal
Port truthful manual IP behavior.

### Deliverables
- manual IP form
- validation
- success/failure messaging
- no misleading connect wording

### iOS parity target
Match the stabilized truthful manual-IP flow from iOS:
- behavior must match wording
- no fake “connected” semantics

### Acceptance
- manual IP flow is understandable
- no duplicate bad entries
- whitespace / invalid-IP handling is clean

---

## C-03 — Saved device lifecycle parity

### Goal
Port save/rename/remove lifecycle.

### Deliverables
- save TV
- rename TV
- remove device
- re-add behavior

### iOS parity target
Preserve the same saved-device lifecycle clarity as iOS.

### Acceptance
- lifecycle is clear and recoverable
- no stale saved-device ambiguity
- remove + re-add behavior is predictable

---

## D-01 — Settings parity

### Goal
Port settings behavior and destructive actions.

### Deliverables
- settings screen
- forget pairing
- remove device
- clear success/failure UX
- no session-breaking surprise

### iOS parity target
Match the stabilized iOS behavior:
- Settings should not unintentionally break active session
- Forget Pairing and Remove Device must feel distinct and trustworthy

### Acceptance
- actions are explicit
- post-success state is clear
- no stale-state ambiguity
- no misleading repeated destructive prompts after success

---

## D-02 — Diagnostics parity

### Goal
Port sanitized diagnostics and optional debug surface.

### Deliverables
- structured logger
- diagnostics summary
- recent events buffer
- debug-only diagnostics surface if chosen

### iOS parity target
Preserve the iOS rules:
- diagnostics must be structured
- diagnostics must be sanitized
- diagnostics must not create secret leakage

### Acceptance
- no secret leakage
- useful and readable diagnostics
- debug/visibility decision is explicit

---

## E-01 — Secure storage parity

### Goal
Port sensitive storage split correctly.

### Deliverables
- DataStore for non-sensitive data
- secure storage for token/SPC artifacts
- migration strategy if needed
- clear pairing artifact reset helpers

### iOS parity target
Mirror the iOS split between normal storage and sensitive pairing/session material.

### Acceptance
- secrets are not plain-stored
- lifecycle actions fully clear intended state
- Forget Pairing / Remove Device semantics stay truthful

---

## E-02 — Legacy encrypted baseline

### Goal
Port the encrypted/JU path.

### Deliverables
- handshake
- PIN flow
- stale credential recovery
- truthful connected-vs-ready logic

### iOS parity target
Use the stabilized JU/SPC iOS behavior as the reference, not older pre-fix behavior.

### Acceptance
- legacy TV path can pair and reconnect
- stale creds do not create fake connected state
- pairing recovery is reliable

---

## E-03 — JU recovery hardening parity

### Goal
Bring over the stabilized JU fixes.

### Deliverables
- same-pass fallback to fresh pairing
- pairing clear cancellation behavior
- settings UX evidence parity
- targeted JU tests

### iOS parity target
Reference the accepted JU stale-pairing/recovery work already stabilized on iOS.

### Acceptance
- no fake connected JU state
- no hidden workaround behavior
- recovery behavior matches iOS intent

---

## F-01 — D-pad and volume polish parity

### Goal
Port refined control feel.

### Deliverables
- D-pad hold/repeat
- volume hold/repeat
- low-latency initial response
- immediate cancel on release

### iOS parity target
Use the stabilized iOS interaction behavior as the source of truth, while still respecting Android-native gesture handling.

### Acceptance
- single tap correct
- hold smooth
- no stuck input
- no hidden aggressive behavior

---

## F-02 — Capability-gating polish

### Goal
Match iOS support-boundary truthfulness.

### Deliverables
- capability checks
- unsupported messaging
- Quick Launch wording
- best-effort labeling if needed

### iOS parity target
Preserve iOS truthfulness:
- unsupported stays unsupported
- best-effort stays best-effort
- Quick Launch stays Quick Launch

### Acceptance
- no overclaiming
- no false feature availability
- copy stays honest

---

## F-03 — Accessibility/localization baseline

### Goal
Bring baseline UX parity.

### Deliverables
- labels/hints
- font scale sanity
- high-value string externalization
- key screen readability

### iOS parity target
Match the iOS accessibility/localization baseline, not a full localization platform.

### Acceptance
- baseline only, not full infra
- important controls remain understandable
- key screens remain usable at large text scale

---

## G-01 — Android E2E framework

### Goal
Create Android-specific manual test framework.

### Deliverables
- E2E checklist
- report template
- bug template
- Android-specific test notes

### iOS parity target
Follow the same discipline as the iOS E2E framework, but adapt for Android device realities and permissions.

### Acceptance
- real-device/manual phase can begin
- reporting format is clear
- truthfulness issues can be tracked cleanly

---

## G-02 — Android release-readiness artifacts

### Goal
Create Android truthfulness docs.

### Deliverables
- support matrix
- release checklist
- release-readiness summary

### iOS parity target
Android docs should be as honest and structured as the iOS docs, but not claim parity before it exists.

### Acceptance
- Android behavior is documented honestly
- known limitations are explicit
- support boundary remains trustworthy
