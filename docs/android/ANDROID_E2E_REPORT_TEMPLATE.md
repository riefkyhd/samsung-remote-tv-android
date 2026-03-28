# Android E2E Report Template

Use this template for full Android E2E passes.

# 1. Run Identity
- Date:
- Branch:
- Commit:
- Build variant:
- Reporter:

---

# 2. Scope and Intent
- E2E objective:
- In-scope flows:
- Explicitly out of scope:

---

# 3. Environment
- Primary device:
- OS version:
- Additional devices (if any):
- TV model(s) / firmware (if available):
- Network context:
- Permission context:

---

# 4. Commands Executed
List exact commands.

- Command:
  - Context:
  - Destination:

---

# 5. Validation Results
- Build:
- Tests:
  - Executed:
  - Passed:
  - Failed:
  - Skipped:
  - Not run:
- Lint / warnings:
- Install / launch:
- E2E flow pass/fail summary:
- Artifact / log paths:

---

# 6. Behavior Verification Notes
- Discovery/manual IP:
- Connection state truthfulness (Connected vs Ready):
- Remote interaction (tap/hold/repeat/no-stuck):
- Capability truthfulness (unsupported blocking / best-effort wording):
- Settings/pairing lifecycle:
- Diagnostics safety/readability:

---

# 7. Issues Found
- Issue ID:
  - Severity:
  - Area:
  - Repro summary:
  - Evidence:
  - Current status:

---

# 8. Caveats and Limits
- Validation mode (local/session vs CI):
- Device/network constraints:
- Real Samsung TV gaps:
- Known deferred work:

---

# 9. Release Recommendation
- Recommendation: (`Go` / `Go with caveats` / `No-Go`)
- Reason:
- Required follow-ups before release:

