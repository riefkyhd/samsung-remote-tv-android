# Android Tasking Prompts for Codex CLI

Use one prompt at a time.

Do not paste this whole file into Codex at once.

## Required iOS Reference

For every Android migration phase, use this iOS repo as the behavioral source of truth:

- https://github.com/riefkyhd/samsung-remote-tv-ios
- Branch: `main`

Default baseline:
- latest accepted iOS stabilization state

Recommended known checkpoints:
- `87b91f4`
- `853fff0`

If a task names a more specific accepted iOS commit, use that as the temporary reference for that phase.

---

## Prompt 1 — Android migration baseline inspection

Create a narrow Android migration baseline plan for this repo.

Context:
- The iOS app is the source of truth for behavior and product truthfulness.
- iOS reference repo: https://github.com/riefkyhd/samsung-remote-tv-ios (branch `main`)
- We are building a native Android app in Kotlin + Jetpack Compose.
- Goal is close functionality and UI parity, not a redesign.

Before editing anything:
1. inspect the repo and current docs
2. explain the Android package/module structure you recommend
3. explain how you will mirror the iOS architecture on Android
4. identify the smallest first implementation slice
5. explain risks and validation approach

Do not generate broad code yet.
Start with a migration plan only.

---

## Prompt 2 — Android app shell and Compose foundation

Implement the Android app shell only.

Context:
- Use the iOS repo as the visual and behavioral reference
- preserve screen hierarchy and product truthfulness

Scope:
- app module setup
- Compose setup
- Hilt setup
- navigation shell
- Discovery / Remote / Settings placeholders
- design token baseline

Do not implement transport logic yet.

Before editing:
- explain affected files
- explain architecture choices
- explain risks
- explain validation approach

Then implement strictly within scope.

---

## Prompt 3 — Modern TV path only

Implement the modern TV path only.

Context:
- Match the iOS modern-TV behavior first
- do not broaden into legacy encrypted/JU yet

Scope:
- modern connect path
- truthful connection state modeling
- D-pad / volume / media / power baseline
- no legacy encrypted/JU path yet

Before editing:
- explain affected files
- explain risks
- explain validation approach

Then implement strictly within scope.

---

## Prompt 4 — Discovery and manual IP parity

Implement Android discovery/manual IP parity.

Context:
- Match iOS discovery behavior and wording as closely as practical
- iOS reference repo remains the source of truth

Scope:
- discovery screen behavior
- saved vs discovered sections
- manual IP flow
- truthful wording
- no misleading connect behavior

Before editing:
- explain affected files
- explain risks
- explain validation approach

Then implement strictly within scope.

---

## Prompt 5 — Settings and pairing reset parity

Implement Android settings and pairing reset parity.

Context:
- Match the stabilized iOS semantics for Forget Pairing and Remove Device
- preserve visible success/failure UX

Scope:
- Forget Pairing
- Remove Device
- clear post-success UX
- no stale-state ambiguity

Before editing:
- explain affected files
- explain risks
- explain validation approach

Then implement strictly within scope.

---

## Prompt 6 — Diagnostics parity

Implement Android diagnostics parity.

Context:
- preserve iOS diagnostics discipline
- keep logs sanitized
- optional debug diagnostics surface is acceptable if it remains safe

Scope:
- structured diagnostics
- sanitized logs
- diagnostics summary
- optional debug diagnostics surface

Do not expose secrets.
Do not reopen unrelated architecture.

Before editing:
- explain affected files
- explain risks
- explain validation approach

Then implement strictly within scope.

---

## Prompt 7 — Legacy encrypted/JU parity spike

Inspect and plan the Android legacy encrypted/JU path.

Context:
- iOS legacy/JU behavior is already stabilized and must be the reference
- do not use pre-fix iOS behavior as the baseline

Goal:
- determine how to port the stabilized iOS JU/SPC behavior into Android

Before editing:
1. inspect the iOS legacy encrypted flow
2. explain the Android equivalent architecture
3. explain storage and pairing lifecycle requirements
4. explain the smallest safe implementation plan
5. explain risks and validation approach

Do not implement immediately.
Plan first.

---

## Prompt 8 — Android interaction polish parity

Implement Android interaction polish parity.

Context:
- use the accepted iOS interaction behavior as the reference
- preserve truthfulness and support boundaries

Scope:
- D-pad hold/repeat
- volume hold/repeat
- haptic baseline
- no stuck input
- truthful unsupported behavior

Before editing:
- explain affected files
- explain risks
- explain validation approach

Then implement strictly within scope.

---

## Prompt 9 — Android capability truthfulness pass

Implement capability truthfulness parity.

Context:
- preserve iOS support-boundary honesty
- do not overclaim parity

Scope:
- unsupported actions blocked truthfully
- Quick Launch wording preserved
- best-effort capabilities labeled honestly
- no installed-app overclaiming

Before editing:
- explain affected files
- explain risks
- explain validation approach

Then implement strictly within scope.

---

## Prompt 10 — Android E2E prep

Create the Android E2E prep docs and plan.

Context:
- use the iOS E2E discipline as a structural reference
- adapt it for Android permissions/device realities

Scope:
- Android E2E checklist
- Android report template
- Android bug triage template
- release-readiness notes

Before editing:
- explain affected files
- explain risks
- explain validation approach

Then implement strictly within scope.

---

## Prompt 11 — Android release-readiness sweep

Do a final Android stabilization/release-readiness sweep.

Context:
- no new features
- docs/support-boundary alignment must remain truthful
- use iOS release-discipline as the reference, not as a copy target

Scope:
- release-critical polish only
- no new features
- docs/support-boundary alignment
- validation evidence

Before editing:
- explain affected files
- explain risks
- explain validation approach

Then implement strictly within scope.

---

## Prompt 12 — Android migration bridge prompt

Use this when resuming work in a new Codex CLI session:

Read these files first:

- AGENTS.md
- docs/android/ROADMAP_ANDROID_MIGRATION.md
- docs/android/FEATURE_PARITY_MATRIX.md
- docs/android/IMPLEMENTATION_BACKLOG_ANDROID.md
- docs/android/UI_PARITY_GUIDE.md
- docs/android/TASKING_PROMPTS_ANDROID.md

Also treat this iOS repo as the behavioral source of truth:
- https://github.com/riefkyhd/samsung-remote-tv-ios
- branch `main`

Treat all of the above as required inputs.
Do not continue until you have incorporated them into your plan.

Use:
- ROADMAP as the phase order
- FEATURE_PARITY_MATRIX as the truthfulness/parity tracker
- IMPLEMENTATION_BACKLOG as the execution queue
- UI_PARITY_GUIDE as the visual/UX reference
- AGENTS as repository rules
- iOS repo as the behavior reference baseline

Now explain:
1. the current Android migration phase
2. the equivalent iOS reference area/behavior you are targeting
3. affected files
4. risks
5. validation approach

Then continue strictly within that framework.