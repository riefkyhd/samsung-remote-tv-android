# Android E2E Checklist

Use this checklist to run Android end-to-end validation with release-discipline evidence.

Reference discipline:
- iOS `QA_CHECKLIST.md` for structure and test rigor
- Android migration docs for truthfulness/support-boundary rules

## 1. Environment and Device Readiness
- [ ] Baseline test device connected: Samsung Galaxy S9 (`SM-G965F`) on Android 10 (API 29)
- [ ] Device and TV are on the same local network
- [ ] App has required Android runtime permissions granted (local network/Wi-Fi related flows as applicable)
- [ ] `adb devices -l` shows target device as `device`
- [ ] Build/install path is known and current (`app/build/outputs/apk/debug/app-debug.apk`)

## 2. Build / Install / Launch
- [ ] `:app:assembleDebug` succeeds
- [ ] APK installs via `adb install -r`
- [ ] App launches via launcher or `adb shell am start`
- [ ] No startup crash or immediate fatal error

## 3. Discovery and Connection
- [ ] Discovery scan runs on local network
- [ ] Manual IP flow validates input and handles errors truthfully
- [ ] Saved vs discovered TV sections remain distinct
- [ ] Connect action semantics remain truthful (`Open` vs `Connect`)
- [ ] Connected vs Ready semantics remain explicit and honest

## 4. Remote Interaction and Capability Truthfulness
- [ ] D-pad tap works when ready
- [ ] D-pad hold/repeat works and stops on release
- [ ] Volume hold/repeat works and stops on release
- [ ] No stuck input after release, screen exit, or disconnect
- [ ] Unsupported actions are blocked truthfully before dispatch/hold start
- [ ] Power remains labeled best-effort
- [ ] Quick Launch remains visible but explicitly unavailable in current baseline
- [ ] Installed-app browsing/enumeration is not overclaimed

## 5. Settings / Pairing Lifecycle
- [ ] Forget Pairing clears pairing/auth material while preserving saved TV
- [ ] Remove Device removes saved TV and clears pairing/auth material
- [ ] Post-success feedback is clear and not stale
- [ ] No ambiguous destructive-action outcomes

## 6. Diagnostics and Safety
- [ ] Diagnostics remain structured and readable
- [ ] Sensitive values are sanitized (no raw tokens, credentials, PINs, secret payloads)
- [ ] Last error and recent events remain coherent with visible behavior

## 7. Legacy/JU and Protocol Boundaries
- [ ] Legacy/JU behavior is not overclaimed beyond implemented scope
- [ ] Unsupported protocol paths show truthful messaging
- [ ] No fake-ready or fake-control behavior appears in unsupported paths

## 8. Release-Readiness Logging Discipline
- [ ] Exact validation commands are captured
- [ ] Device/destination context is captured
- [ ] Executed/passed/failed/skipped/not-run counts are captured
- [ ] Artifact/log paths are captured
- [ ] Known limitations and caveats are documented explicitly
- [ ] Evidence is labeled local/session-based if CI evidence is unavailable

## 9. Required Manual Evidence Bundle
- [ ] Build output summary
- [ ] Unit/instrumentation/lint summary
- [ ] Device install/launch evidence
- [ ] Notes on real Samsung TV behavior checks
- [ ] Remaining caveats and recommended next step
