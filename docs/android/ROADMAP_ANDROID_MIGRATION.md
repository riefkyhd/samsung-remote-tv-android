# Android Migration Roadmap

## Goal

Build a native Android version of the Samsung TV remote app that stays as close as possible to the iOS app in:

- feature behavior
- UX flow
- connection/pairing truthfulness
- support boundaries
- diagnostics discipline
- settings/recovery behavior

This is not a redesign project.
This is a migration and parity project.

---

## iOS Reference Baseline

Primary reference repo:
- https://github.com/riefkyhd/samsung-remote-tv-ios
- Branch: `main`

Use the latest accepted iOS stabilization state as the default parity target unless a specific commit is named for a phase.

Recommended reference points:
- `87b91f4` — final v1 stabilization sweep
- `853fff0` — later accepted iOS state including follow-up JU/UX-related work discussed after v1 stabilization

Android should mirror iOS behavior as closely as practical unless Android platform constraints require a justified divergence.

---

## Target Stack

- Kotlin
- Jetpack Compose
- ViewModel + StateFlow
- Hilt
- Navigation Compose
- DataStore
- secure storage for secrets via Android Keystore + Tink or equivalent

---

## Migration Strategy

Do not port everything at once.

Use controlled phases.

---

## Android Baseline

- Minimum supported Android version: API 24 (Android 7.0)
- Important validation target: Samsung Galaxy S9 on Android 10 (API 29)
- Android migration should be tested against real-device behavior on Galaxy S9, not emulator-only behavior

---

## Phase A — App Shell and Design Parity

### Goal
Create the Android shell and match the visual/structural feel of the iOS app.

### Deliverables
- app module structure
- theme tokens
- navigation shell
- Discovery screen skeleton
- Remote screen skeleton
- Settings screen skeleton
- reusable control components

### Acceptance
- app builds
- screens navigate
- major layout hierarchy matches iOS reference
- design tokens exist

---

## Phase B — Modern TV Core Control Path

### Goal
Ship useful Android behavior quickly via the modern path first.

### Deliverables
- Samsung TV model
- modern connect path
- remote key sending
- basic connection states
- D-pad / volume / media / power
- save device
- reconnect baseline

### Acceptance
- modern TV can connect and send core commands
- state labels are truthful
- saved-device reconnect works

---

## Phase C — Discovery and Saved Device Parity

### Goal
Match iOS discovery and saved-device experience.

### Deliverables
- discovery screen behavior
- manual IP flow
- saved TVs
- rename TV
- remove device
- capability-aware row rendering

### Acceptance
- discovery works on supported networks
- manual IP flow is truthful
- saved-device lifecycle works clearly

---

## Phase D — Settings, Pairing Reset, and Diagnostics

### Goal
Bring Android to parity with iOS stabilization work.

### Deliverables
- Forget Pairing
- Remove Device
- visible success/failure UX
- diagnostics logging
- debug diagnostics surface if appropriate
- sanitized logs only

### Acceptance
- no stale false-connected state in normal supported flows
- destructive-action UX is clear
- diagnostics are safe and useful

---

## Phase E — Legacy Encrypted / JU Parity

### Goal
Port the hard-value legacy encrypted flow.

### Deliverables
- legacy/SPC handshake
- PIN flow
- stale pairing recovery
- same-attempt fallback to fresh pairing
- truthful connected-vs-ready state
- JU-specific recovery behavior

### Acceptance
- JU or equivalent legacy TV path works
- stale creds do not create fake connected state
- pairing recovery is reliable

---

## Phase F — Interaction Polish Parity

### Goal
Port the refined interaction behavior from iOS.

### Deliverables
- D-pad hold/repeat
- volume hold/repeat
- haptics
- capability gating
- diagnostics readability
- accessibility baseline
- localization baseline

### Acceptance
- controls feel responsive
- unsupported actions remain truthful
- accessibility baseline is present

---

## Phase G — Android E2E and Release Readiness

### Goal
Treat Android as a release candidate and validate it like a real app.

### Deliverables
- Android E2E checklist
- bug triage report
- release-readiness summary
- support matrix alignment
- manual test evidence

### Acceptance
- no P0 blockers
- known limitations documented
- release recommendation possible

---

## Non-Negotiable Rules

- Keep Quick Launch truthful.
- Do not overclaim installed-app enumeration.
- Do not reopen stabilized behavior casually.
- Keep docs aligned with implementation.
- Do not promise CI-grade evidence when only local/session evidence exists.

---

## Deferred / Out of Scope by Default

These are not default migration goals unless explicitly requested:

- keyboard input feature
- pointer-mode deep investigation
- cloud control
- internet/off-LAN control
- giant shared core rewrite
- Flutter / React Native rewrite
- broad protocol re-architecture

---

## Recommended Order of Execution

1. shell + design tokens
2. modern core control
3. discovery + saved devices
4. settings + diagnostics
5. legacy encrypted path
6. interaction polish
7. E2E + release

This order maximizes usable value early without losing long-term parity.