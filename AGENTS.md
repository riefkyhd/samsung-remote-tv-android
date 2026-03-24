# AGENTS.md

This repository contains the Android migration of the Samsung TV remote app.

## Mission

Build a native Android app in Kotlin + Jetpack Compose that matches the current iOS app as closely as possible in:

- user-visible behavior
- screen structuree
- interaction model
- capability boundaries
- pairing/recovery truthfulness
- diagnostics safety
- release-readiness discipline

Do not expand scope into unrelated new features unless explicitly requested.

---

## Android Baseline

- Minimum supported Android version: API 24 (Android 7.0)
- Important validation target: Samsung Galaxy S9 on Android 10 (API 29)
- Android migration should be tested against real-device behavior on Galaxy S9, not emulator-only behavior

---

## iOS Reference Repository

Primary behavioral reference for this Android migration:

- Repo: https://github.com/riefkyhd/samsung-remote-tv-ios
- Branch: `main`
- Reference baseline: use the latest accepted iOS stabilization state unless a more specific commit is provided

Recommended known iOS baseline checkpoints:
- `87b91f4` — final v1 stabilization sweep baseline
- `853fff0` — includes later JU/UX-related work already discussed
- If a newer accepted iOS commit is explicitly provided in a task, use that commit as the temporary reference point for that phase.

Rules:
- Treat the iOS repo as the source of truth for behavior, UX flow, capability truthfulness, pairing/reset semantics, diagnostics discipline, and release-boundary wording.
- Do not copy blindly if Android platform constraints require a different implementation, but preserve user-visible behavior as closely as practical.
- If Android intentionally diverges from iOS behavior, explain why in the task summary and update parity tracking docs if needed.

---

## Source of Truth

The iOS app is the behavioral reference.

Android work must stay aligned with:

- screen structure and UX flow from the iOS app
- product truthfulness already established in iOS
- support boundaries already established in iOS docs
- recovery behavior already stabilized in iOS
- Quick Launch truthfulness: curated shortcuts, not installed-app enumeration

If Android behavior intentionally differs, explain why in the task summary.

---

## Architectural Rules

Use Clean Architecture style with a clear split between:

- `data/`
- `domain/`
- `presentation/`
- `core/`

Recommended direction:

- Kotlin
- Jetpack Compose
- ViewModel + StateFlow
- Hilt for DI
- Navigation Compose
- DataStore for non-sensitive persistence
- Android Keystore + Tink or equivalent secure storage approach for secrets

### Boundaries

- Presentation should talk to use cases, not concrete repositories directly.
- Repository implementations stay in `data/`.
- Domain models should stay platform-neutral where practical.
- Avoid leaking Android framework types into domain layer unless clearly justified.

---

## Scope Control

Prefer this implementation order:

1. app shell + design parity
2. modern TV path
3. discovery/manual IP/saved devices
4. settings and diagnostics parity
5. legacy encrypted/JU path parity
6. polish parity

Do not attempt full parity in one giant pass.

Break work into narrow phases with clear acceptance criteria.

---

## Product Truthfulness Rules

Do not overclaim capabilities.

Non-negotiable rules:

- Do not rename **Quick Launch** back to “installed apps” unless real installed-app enumeration exists.
- Do not claim unsupported capabilities as supported.
- If a feature is best-effort, label it as best-effort.
- If a behavior differs by TV generation or protocol, surface that honestly in docs and UI where appropriate.

---

## Security Rules

Never log raw:

- tokens
- credentials
- PIN values
- secret payloads
- sensitive device identifiers without redaction

Diagnostics must be sanitized.

Sensitive storage must not be kept in plain preferences.

---

## Transport and Protocol Rules

Treat modern and legacy paths separately.

- Do not let fixes for legacy/JU destabilize modern TVs.
- Do not assume a capability exists on all generations.
- Capability gating must remain explicit and truthful.
- Avoid broad transport rewrites unless explicitly requested.

---

## UI / UX Rules

Goal: Android should feel as close as possible to iOS while still feeling native enough for Android.

Priorities:

- same screen hierarchy
- same major controls and control grouping
- same connection-state truthfulness
- same destructive-action clarity
- same diagnostics discipline
- same support-boundary honesty

Do not let default Material styling redefine the product accidentally.
Use design tokens and controlled styling.

---

## Validation Standard

For every significant checkpoint, provide:

- exact command(s) run
- build/test context
- destination if available
- executed / passed / failed / skipped counts
- limitations encountered

Do not claim “fully verified” without real execution evidence.

If validation is local/session-based rather than CI-hosted, state that explicitly.

---

## Codex CLI Working Style

When given a task:

1. inspect first
2. explain affected files
3. explain the smallest safe plan
4. explain risks
5. implement strictly within scope
6. validate
7. summarize changes and remaining caveats

Do not jump into broad refactors without first explaining why.

---

## No-Drift Rules

Do not regress:

- Presentation -> Repository direct coupling
- diagnostics safety
- support-boundary truthfulness
- Quick Launch wording
- saved-device / pairing lifecycle clarity

If docs need to change because behavior changes, update docs in the same phase.

---

## If Unsure

Prefer:

- smaller patch
- clearer truthfulness
- narrower scope
- explicit caveat
- stronger validation evidence