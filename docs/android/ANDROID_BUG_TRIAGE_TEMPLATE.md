# Android Bug Triage Template

Use this template for Android migration defects found during E2E or stabilization.

## 1. Metadata
- Bug ID:
- Title:
- Date reported:
- Reporter:
- Owner:
- Prompt/phase:

## 2. Classification
- Severity: (`P0` / `P1` / `P2` / `P3`)
- Priority: (`High` / `Medium` / `Low`)
- Area: (`Discovery` / `Remote` / `Settings` / `Pairing` / `Diagnostics` / `Build/Infra` / `Docs`)
- Type: (`Functional` / `Truthfulness` / `UX` / `Performance` / `Security` / `Regression`)

## 3. Environment
- App build/commit:
- Device model:
- Android version:
- TV model/firmware (if relevant):
- Network context:

## 4. Reproduction
- Preconditions:
- Steps to reproduce:
  1.
  2.
  3.
- Expected result:
- Actual result:
- Repro rate:

## 5. Evidence
- Logs/artifacts:
- Screenshot/video:
- Related test/report path:

## 6. Truthfulness / Support-Boundary Impact
- Does this risk overclaiming capability? (`Yes` / `No`)
- Does this misstate Connected vs Ready semantics? (`Yes` / `No`)
- Does this affect Quick Launch wording/truthfulness? (`Yes` / `No`)
- Does this affect unsupported/best-effort honesty? (`Yes` / `No`)
- Notes:

## 7. User and Release Impact
- User impact summary:
- Release impact summary:
- Blocker status: (`Release blocker` / `Non-blocker`)

## 8. Resolution Plan
- Proposed fix:
- Risk of fix:
- Test plan:
- Target phase/milestone:

## 9. Verification and Closure
- Fix commit(s):
- Verification commands:
- Verified on device(s):
- Remaining caveat (if any):
- Final status: (`Open` / `In Progress` / `Resolved` / `Closed`)
