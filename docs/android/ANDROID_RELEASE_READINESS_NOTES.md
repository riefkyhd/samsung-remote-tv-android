# Android Release Readiness Notes

This note defines release-readiness expectations for Android migration phases.

## 1. Purpose
- Keep release recommendations evidence-based.
- Preserve support-boundary honesty from iOS reference discipline.
- Avoid parity overclaiming before real-device/real-TV confirmation.

## 2. Required Readiness Gates

### Gate A — Scope and Documentation Integrity
- `README`/docs claims match implementation.
- Support-boundary wording is truthful.
- Quick Launch remains curated shortcuts, not installed-app enumeration.
- Best-effort behavior is labeled where applicable.

### Gate B — Validation Evidence Quality
- Exact commands are recorded.
- Device/destination context is recorded.
- Executed/passed/failed/skipped/not-run counts are recorded.
- Artifact/log paths are recorded.
- Limitations are explicitly stated.

### Gate C — Product Behavior Truthfulness
- Connected vs Ready semantics are preserved.
- Unsupported actions are blocked truthfully.
- Pairing/reset lifecycle behavior remains explicit and honest.
- Diagnostics remain sanitized and readable.

### Gate D — Security and Safety
- No raw sensitive values in logs.
- Sensitive storage behavior remains within approved secure paths.
- Destructive actions preserve clear, explicit outcomes.

## 3. Minimum Device Evidence for Recommendation
- Baseline Android device run completed (Galaxy S9 / Android 10).
- Install + launch evidence captured.
- Connected instrumentation evidence captured.
- At least one real Samsung TV validation pass for behavior-sensitive release decisions.

## 4. Recommendation Levels
- `Go`: all readiness gates pass, no release blockers, caveats documented.
- `Go with caveats`: no blockers, but non-blocking gaps remain with explicit owner/follow-up.
- `No-Go`: release blockers exist or truthfulness boundaries are unclear/unverified.

## 5. Local-Only Validation Caveat Language
Use this when CI/shared artifacts are unavailable:
- "Validation evidence is local/session-based and not CI-hosted."
- "Results are bounded by current device/network context."
- "Real Samsung TV validation gaps remain explicitly listed."

## 6. Non-Negotiable No-Drift Rules
- Do not claim unsupported capabilities as supported.
- Do not imply transport/protocol parity before it exists.
- Do not regress Quick Launch wording truthfulness.
- Do not obscure known limitations in release summaries.
