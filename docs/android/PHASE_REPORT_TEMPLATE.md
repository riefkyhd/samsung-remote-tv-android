# Phase Report Template

## 1. Phase / Prompt
- Phase name:
- Prompt name:
- Date:

---

## 2. Scope Summary
- Intended scope:
- Explicitly out of scope:
- Scope stayed disciplined: Yes / No

---

## 3. Files Changed
- path/to/file1
- path/to/file2
- path/to/file3

---

## 4. What Actually Changed
- Describe the real implementation changes.
- Focus on behavior and architecture impact, not just filenames.
- Note any user-visible behavior changes clearly.

Example:
- Added connected-but-not-ready state handling for legacy path.
- Replaced raw debug prints with sanitized structured diagnostics.
- Added D-pad hold/repeat with immediate cancellation on release.

---

## 5. Root Cause / Reasoning
- What was the issue or need before this phase?
- What likely caused it?
- Why was this implementation chosen?
- If alternatives existed, why were they not chosen?

---

## 6. Risks Considered
- What regressions were considered?
- What platform-specific or UX risks exist?
- What was intentionally deferred?
- Any truthfulness/support-boundary risks?

---

## 7. Validation Commands
List the exact commands run.

- Command:
    - Context:
    - Destination if available:

Examples:
- `./gradlew :app:assembleDebug`
- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:lintDebug`

Or for iOS:
- `mcp__xcode-tools__BuildProject`
- `mcp__xcode-tools__RunAllTests`
- `mcp__xcode-tools__XcodeListNavigatorIssues(severity: "warning")`

---

## 8. Validation Results
- Build:
- Tests:
    - Executed:
    - Passed:
    - Failed:
    - Skipped:
    - Not run:
- Lint / warnings:
- Artifact / log path:
- Limitations encountered:

Be explicit if validation is local/session-based and not CI-hosted.

---

## 9. Manual / Real-Device Validation
- What was manually tested:
- Device(s):
- OS version(s):
- Result:
- What still needs real-device confirmation:

Examples:
- Verified haptic feedback on physical iPhone.
- Verified JU stale-pairing recovery on real TV.
- Android shell tested on Galaxy S9 / Android 10.

---

## 10. Remaining Caveats
- What is still not fully proven?
- What limitations remain?
- Anything documented rather than fixed?
- Is there a known UX caveat?

---

## 11. Status
Choose one:
- Accepted
- Accepted pending manual verification
- Implemented candidate, not complete
- Needs follow-up

Reason:

---

## 12. Recommended Next Step
- Close phase
- Small follow-up
- Move to next prompt
- Run manual verification
- Update docs
- Open blocker fix