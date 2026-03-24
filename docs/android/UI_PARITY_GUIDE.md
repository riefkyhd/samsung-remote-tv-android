# UI Parity Guide

## Goal

Android should feel as close as possible to the iOS app in layout, control grouping, and product behavior.

This does not mean blindly copying iOS conventions.
It means preserving:

- information hierarchy
- action hierarchy
- control placement logic
- state truthfulness
- product tone

---

## iOS Reference Baseline

Primary iOS reference repo:
- https://github.com/riefkyhd/samsung-remote-tv-ios
- Branch: `main`

Default visual/behavioral reference:
- latest accepted iOS stabilization state

Recommended known checkpoints:
- `87b91f4` — final v1 stabilization baseline
- `853fff0` — later accepted iOS state including JU/UX follow-up work

If Android intentionally diverges visually or behaviorally, document the reason in the task summary.

---

## Core Screens to Mirror

### 1. Discovery
Preserve:
- saved TVs vs discovered TVs distinction
- manual IP entry flow
- empty state clarity
- truthful action wording

### 2. Remote
Preserve:
- clear connection-state feedback
- grouped controls
- D-pad prominence
- quick access to settings
- quick launch placement
- diagnostics only where intended

### 3. Settings
Preserve:
- clear destructive actions
- visible post-success state
- pairing lifecycle clarity
- remove-vs-forget distinction

---

## Visual Priorities

### Keep Close
- major layout regions
- card grouping
- control sizes
- icon scale
- spacing rhythm
- semantic color meaning
- destructive-action emphasis
- success-state emphasis

### Adapt Carefully
- Android back behavior
- sheet vs dialog equivalents
- typography defaults
- ripple/haptic conventions
- Material surface styling

---

## Design Tokens to Define Early

- `SpacingXs`
- `SpacingSm`
- `SpacingMd`
- `SpacingLg`
- `RadiusSm`
- `RadiusMd`
- `RadiusLg`
- `IconSm`
- `IconMd`
- `IconLg`

Semantic colors:
- primary
- surface
- surfaceVariant
- destructive
- success
- warning
- textPrimary
- textSecondary

---

## Product Meaning Must Stay the Same

Android can look Android-native, but it must not change product meaning.

Do not change:
- which actions feel primary vs secondary
- when the app implies “Connected” vs “Ready”
- how destructive actions are labeled
- how support boundaries are communicated
- how Quick Launch is framed

---

## Component Parity Targets

### D-pad
- same directional prominence
- same central OK/Select emphasis
- same hold behavior once implemented
- same disabled/unsupported truthfulness

### Volume / Media Controls
- should feel grouped and secondary to D-pad
- same fast-access positioning

### Quick Launch
- must remain explicitly “Quick Launch”
- should not look like installed-app browser
- curated shortcut framing must remain clear

### Diagnostics
- debug-only if you choose that route
- must stay readable
- must never expose secrets

---

## State Truthfulness Rules

Android must preserve the same truthfulness as iOS:

- Connected is not always Ready
- unsupported does not mean hidden if explanation is better
- best-effort remains best-effort
- destructive actions must show clear result
- pairing lifecycle must be explicit

---

## Do Not Regress These UX Fixes

- no fake connected state after stale pairing
- no silent hidden workaround behavior
- no ambiguous Forget Pairing state
- no overclaiming installed-app support
- no unreadable diagnostics
- no unclear saved-vs-discovered distinction

---

## Compose Implementation Notes

- do not let Material defaults dominate product identity
- use custom composables where needed
- preserve interaction timing/feedback where possible
- use stable state modeling rather than ad-hoc UI flags when possible

---

## Validation Checklist

Before calling a screen “parity-ready,” confirm:

- major layout structure matches iOS intent
- wording is truthful
- action hierarchy is clear
- destructive actions are obvious
- states are not misleading
- accessibility baseline is present
- Android-specific adaptation does not change product meaning

---

## Android-Specific Adaptation Rules

Allowed:
- Android-native navigation handling
- Android-native permission flow
- Android-native dialog/sheet presentation choices
- Android-native haptic/ripple conventions

Not allowed:
- changing product truthfulness
- downgrading lifecycle clarity
- hiding limitations that iOS already documents honestly
- converting Quick Launch into a misleading installed-app browser