# Android Feature Parity Matrix

Use this file to track Android parity against the iOS reference.

## Status Legend

- Not Started
- In Progress
- Partial
- Complete
- Best-effort
- Deferred

---

## Reference Baseline

Primary iOS reference repo:
- https://github.com/riefkyhd/samsung-remote-tv-ios
- Branch: `main`

Default parity baseline:
- latest accepted iOS stabilization state

Recommended known reference commits:
- `87b91f4` — final v1 stabilization sweep baseline
- `853fff0` — later accepted iOS state discussed after stabilization

If a task or phase explicitly names a newer accepted iOS commit, use that as the temporary parity target for that phase.

---

## Android Baseline

- Minimum supported Android version: API 24 (Android 7.0)
- Important validation target: Samsung Galaxy S9 on Android 10 (API 29)
- Android migration should be tested against real-device behavior on Galaxy S9, not emulator-only behavior

---

## Migration Snapshot (2026-03-25)

- Active baseline commit for Android parity checks: `853fff0`
- Current Android phase status: A-01 In Progress, A-02 In Progress
- Implemented in this checkpoint:
  - Compose + Hilt + Navigation shell
  - clean package boundaries: `core`, `data`, `domain`, `presentation`
  - placeholder Discovery / Remote / Settings screens
  - token baseline (spacing, radii, icon sizes, semantic colors, typography, button styles)
  - local validation evidence: `assembleDebug`, `testDebugUnitTest`, `lintDebug`
- Not yet implemented in this checkpoint:
  - real discovery/manual IP behavior
  - real transport/pairing behavior
  - settings lifecycle parity behavior

---

## Discovery

| Feature | iOS Status | Android Target | Notes |
|---|---|---|---|
| Same-network discovery | Complete |  | Prefer NSD/mDNS first |
| Manual IP add | Complete |  | Must stay truthful |
| Discovery empty state | Complete |  | Match iOS UX |
| Saved TVs vs discovered TVs | Complete |  | Keep UX clear |
| Duplicate suppression | Complete |  | Important for trust |

---

## Saved Device Lifecycle

| Feature | iOS Status | Android Target | Notes |
|---|---|---|---|
| Save TV | Complete |  |  |
| Rename TV | Complete |  |  |
| Remove Device | Complete |  | Must fully match semantics |
| Forget Pairing | Complete |  | Must stay explicit |
| Re-add after removal | Complete |  |  |

---

## Connection / Pairing

| Feature | iOS Status | Android Target | Notes |
|---|---|---|---|
| Modern TV connect | Complete |  | First Android milestone |
| Truthful connected vs ready | Complete |  | Important |
| Reconnect flow | Complete |  |  |
| Legacy encrypted pairing | Complete |  | Later phase |
| PIN flow | Complete |  |  |
| Stale pairing recovery | Complete |  | Must not regress |
| Same-pass fallback to fresh pairing | Complete |  | Legacy phase |

---

## Remote Controls

| Feature | iOS Status | Android Target | Notes |
|---|---|---|---|
| D-pad tap | Complete |  |  |
| D-pad hold/repeat | Complete |  |  |
| Volume tap | Complete |  |  |
| Volume hold/repeat | Complete |  |  |
| Media controls | Complete |  |  |
| Power / Wake | Best-effort |  | Keep docs honest |
| Mute | Complete |  |  |
| Number pad | Capability-gated |  |  |

---

## Quick Launch / Apps

| Feature | iOS Status | Android Target | Notes |
|---|---|---|---|
| Quick Launch curated shortcuts | Complete |  | Must keep wording |
| Installed-app enumeration | Unsupported |  | Do not overclaim |
| Capability-aware launch UX | Complete |  |  |

---

## Settings / UX

| Feature | iOS Status | Android Target | Notes |
|---|---|---|---|
| Settings does not break session | Complete |  |  |
| Forget Pairing visible evidence | Complete |  |  |
| Remove Device visible evidence | Complete |  |  |
| Success/failure alerts | Complete |  |  |
| Destructive actions clear state | Complete |  |  |

---

## Diagnostics

| Feature | iOS Status | Android Target | Notes |
|---|---|---|---|
| Structured diagnostics | Complete |  |  |
| Sanitized logs | Complete |  | Required |
| Debug diagnostics surface | Complete |  | Optional for Android, but parity preferred |
| Diagnostics readability | Complete |  |  |

---

## Capability Model

| Feature | iOS Status | Android Target | Notes |
|---|---|---|---|
| Capability gating exists | Complete |  |  |
| Unsupported actions blocked truthfully | Complete |  |  |
| Modern vs encrypted capability differences | Complete |  |  |
| Trackpad/pointer best-effort logic | Partial / Best-effort |  | Investigate carefully |

---

## Accessibility / Localization

| Feature | iOS Status | Android Target | Notes |
|---|---|---|---|
| Accessibility labels baseline | Complete |  |  |
| Large text readability baseline | Complete |  | Android font scale parity |
| Localization baseline | Complete |  | Baseline only |
| Full localization infra | Deferred |  | Not required initially |

---

## Validation / Release

| Feature | iOS Status | Android Target | Notes |
|---|---|---|---|
| Local test evidence | Complete |  |  |
| E2E checklist | Complete |  | Create Android version later |
| Release-readiness summary | Complete |  | Create Android version later |
| CI-hosted validation artifacts | Deferred |  | Good follow-up item |

---

## Android Migration Rules

- Android should not ship a weaker truthfulness model than iOS.
- If Android cannot support a feature yet, mark it Partial / Best-effort / Deferred instead of pretending parity exists.
- Update this matrix when behavior meaningfully changes.
