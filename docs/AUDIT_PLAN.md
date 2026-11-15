# LanGhost — Engineering Roadmap & Audit Register

> Status: audit and stabilization in progress  
> Baseline branch: main  
> Baseline commit reviewed: c8e13219461fd1488ccbb83b4fa5ab721ba3eb62  
> Working branch: work/foundation-stabilization

## Project direction

LanGhost is intended to become a polished Android network-inspection, monitoring, and network-intelligence tool for networks and devices the user owns or is authorized to assess.

There are no artificial constraints requiring us to preserve the current implementation. Refactor or replace individual modules, architectural layers, or the application structure when evidence shows that doing so materially improves correctness, security, maintainability, performance, accessibility, or user experience. Avoid rewrites for their own sake.

## Working principles

1. Audit before changing behavior; record evidence, impact, severity, and reproduction steps.
2. Work on feature/audit branches. Do not force-push or make unreviewed changes to main.
3. Keep changes scoped and reviewable. Explain architectural decisions and migration risks.
4. A feature is not complete merely because it compiles: verify behavior with tests and, where required, a real Android device and real network.
5. Never present heuristic network observations as certainty. Show confidence, limitations, permissions, and scan coverage.
6. Network discovery and security checks must be bounded to user-selected or otherwise authorized networks and targets.
7. Protect privacy: disclose external services and transmitted data; minimize collection, retention, and permissions.
8. Keep documentation, version information, changelog, and CI behavior aligned with the actual product.

## Roadmap

### Phase 1 — Build and project foundation
- [x] Reproduce the baseline APK build failure and capture the compiler output; the invalid Compose Modifier arguments were identified and corrected on this branch.
- [x] Restore the Gradle Wrapper (gradlew, gradlew.bat, wrapper JAR and properties); verify it in GitHub Actions.
- [x] Verify the project with JDK 17, Android SDK 35, Gradle 8.9, Android Lint and unit-test tasks in CI. Latest CI run completed successfully: lint, unit tests, Debug and Release APK.
- [x] Correct the confirmed baseline compile errors.
- [x] Replace the legacy workflow with branch/PR validation and tagged-release workflows; install Android command-line tools explicitly and upload Debug/Release APK artifacts.
- [x] Separate pull-request/branch validation from tagged release publication.
- [x] Confirm Debug and Release APK builds both pass on the latest validated working-branch commit; signing remains a release-readiness task.
- [x] Add baseline Android Lint and JUnit test tasks.

### Phase 2 — Network engine
- [x] Implement IPv4 CIDR calculation, contiguous-mask validation, /31 and /32 handling, bounded host enumeration; IPv6 strategy remains open.
- [ ] Review discovery methods (ARP, TCP probes), timeouts, cancellation, false negatives, DNS/NetBIOS fan-out and port-scan concurrency. Bounded orchestration and cancellation handling implemented; real-device validation remains open. Bounded/cancellable TCP-probe orchestration, bounded device enrichment and bounded port scanning are implemented; 35 tests passed in CI on commit 1df8efe. Shared Wi-Fi scan integration and 7 classifier tests are now on the branch and await their own CI run.
- [x] Bound discovery candidates to 1,024 and concurrent host probes to 32; add per-host time budget and explicit cancellation propagation. Socket-level cancellation remains bounded by connect timeout.
- [x] Scope device identities to a hashed logical network key (SSID + gateway + subnet), not BSSID; preserve legacy customizations and isolate scan diffs by network. Random-MAC rotation still cannot be correlated reliably.
- [ ] Validate port scanning, service identification and banner/fingerprint handling.
- [x] Show checked/total IPv4 addresses, subnet and truncation warning; physical-device validation remains open.
- [ ] Test across different routers, subnet sizes, Android versions and network conditions (including mesh roaming and MAC randomization).

### Phase 3 — Security, privacy and permissions
- [ ] Audit manifest permissions and request each permission only when needed. Fine and coarse location are now requested together for Wi-Fi scan flows; notification permission is requested only when enabling new-device notifications. Further API-level device validation remains open.
- [ ] Review cleartext traffic, TLS, external endpoints, redirects, response-size limits and timeouts.
- [ ] Audit local data storage, backup/export paths, logs and sensitive identifiers.
- [ ] Verify biometric/app-lock behavior across lifecycle, process recreation and unavailable hardware.
- [x] Review widget/receiver exposure and exported component requirements; widget updates remain on the exported AppWidgetProvider, while the scan action moved to a non-exported receiver with goAsync().
- [x] Reassess security scoring and threat heuristics; scores are withheld until device discovery, bounded service-port checks and a fresh nearby-Wi-Fi scan have completed. Current Wi-Fi security is included. Further calibration remains open.
- [x] Remove the assumption that a repeated SSID on another BSSID is an Evil Twin; compare only known, clearly different security families and label the result as a low-confidence configuration signal. Real-world mesh validation remains open.
- [ ] Document what is and is not detectable on stock Android.

### Phase 4 — Architecture, data and background work
- [ ] Review repository boundaries, state ownership, ViewModels and dependency construction.
- [ ] Define durable models and persistence for devices, scan history, ping history and profiles.
- [ ] Bound history/cache growth and define retention/cleanup behavior.
- [x] Fix WorkManager cancellation propagation and request notification permission only when the notification feature is enabled. Scheduling constraints, battery impact and device validation remain open.
- [ ] Verify widget refresh and asynchronous broadcast lifecycle.
- [ ] Review error handling, offline behavior, concurrency safety and process-death recovery.
- [ ] Add structured logging that does not leak sensitive network data.

### Phase 5 — Full UI/UX and visual redesign
- [ ] Audit all screens, navigation paths, empty/loading/error states and interaction consistency.
- [ ] Define a coherent design system: color, typography, spacing, shapes, elevation, icons and reusable components.
- [ ] Redesign dashboard around clear network status, actionable insights and live/refreshable data.
- [ ] Improve device list/detail, topology, Wi-Fi analysis, security, tools, profiles and settings.
- [ ] Improve charts and topology visualization without sacrificing performance or accessibility.
- [ ] Add restrained motion, transitions, loading feedback and haptics where useful.
- [ ] Review dark theme and decide whether a complete light theme is worthwhile.
- [ ] Optimize layouts for compact phones, large phones, tablets, landscape and system font scaling.
- [ ] Verify contrast, touch targets, TalkBack semantics and localization.

### Phase 6 — Automated and device testing
- [x] Add initial unit tests for pure network calculations, identity merging, scoring, diff and discovery orchestration (35 tests passed in CI on 1df8efe; 8 Wi-Fi security classifier tests and three security-score/transition regressions added afterward and await CI).
- [ ] Add repository/integration tests for persistence, scanning orchestration and failure cases.
- [ ] Add Compose UI tests for critical flows and permission states.
- [ ] Run Android Lint and dependency/security checks in CI.
- [ ] Test on representative Android API levels and at least one physical device.
- [ ] Exercise real-network scenarios: no Wi-Fi, VPN active, mobile data, guest network, mesh, IPv6, denied permissions and interrupted scans.
- [ ] Add regression tests for every confirmed defect.

### Phase 7 — Advanced network intelligence
- [ ] More reliable device classification with confidence and evidence.
- [ ] Network topology with clear distinction between observed and inferred links.
- [ ] Device presence timeline and change detection.
- [ ] Monitoring for selected devices, latency/loss history and meaningful alerts.
- [ ] Wi-Fi channel/interference analysis with band, width and platform limitations represented accurately.
- [ ] Security insights and actionable remediation guidance.
- [ ] Network diagnostics (DNS, gateway, DHCP, reachability and related tools).
- [ ] Exportable reports and network profiles with privacy controls.

### Phase 8 — Release readiness
- [ ] Final performance, battery, accessibility and privacy review.
- [ ] Verify versioning, release notes, reproducible CI outputs and artifact retention.
- [ ] Configure secure release signing and document key handling.
- [ ] Update README, permissions explanation, feature list, screenshots and troubleshooting.
- [ ] Verify clean clone → build → install → first-run flow.
- [ ] Publish only after explicit release approval.

## Initial verified baseline observations

These are repository observations, not a complete audit verdict:

- The latest reviewed main commit is c8e13219461fd1488ccbb83b4fa5ab721ba3eb62.
- The baseline GitHub Actions run #33 (43682690627) failed during Kotlin compilation. The compiler output identified invalid named Modifier arguments in LockGate.kt and SecurityScreen.kt. These were corrected on this branch; validation runs #2 (43683564098) and #3 (43683602775) both completed successfully, including Debug APK artifact upload.
- The repository contains gradle/wrapper/gradle-wrapper.properties, but the recursive tree reviewed did not contain gradlew, gradlew.bat, or gradle/wrapper/gradle-wrapper.jar.
- The README still describes several features already present in the source tree as roadmap items. Documentation needs reconciliation.
- Release build/signing and test coverage require a dedicated review; do not infer readiness from the existence of a release build type.

## Audit register

Use one entry per finding. Do not mark a suspected issue as confirmed without source evidence, a reproducible test, or a reliable platform constraint.

| ID | Area | Finding | Severity | Evidence / reproduction | Status |
|---|---|---|---|---|---|
| BUILD-001 | Build | Baseline Kotlin compilation failed on invalid Compose named arguments | Blocker | Baseline Actions run #33; corrected LockGate.kt and SecurityScreen.kt; validation runs #2 and #3 succeeded | Resolved on audit branch; verify again after later code changes |
| BUILD-002 | Build | Gradle Wrapper scripts/JAR absent from reviewed repository tree | High | Recursive tree at baseline commit; official Wrapper files and Gradle 8.9 distribution checksum added and verified in CI | Resolved on working branch; main unchanged |
| BUILD-003 | CI | Legacy build workflow grants contents:write to the whole job and publishes a prerelease on every successful main push | Medium | Replaced with branch/PR build workflow and tag-only release workflow; verify permissions and release behavior during final CI review | Resolved on working branch; main unchanged |
| TEST-001 | Testing | No unit or instrumentation test source files are present in the repository tree | High | Added 53 JUnit tests authored across Device, NetworkInfo, ChannelAnalyzer, SecurityAudit, ScanDiff, ScanCoverage, HostDiscovery, Ipv4Subnet, NetworkIdentity, WifiChannel and WifiSecurity; 35 passed in CI on commit 1df8efe; 18 later tests await latest CI | Partially resolved on working branch; CI confirmation pending |
| NET-001 | Discovery | IPv4 scan built a /24-style prefix from the first three octets | High | Ipv4Subnet validates masks and handles unsigned IPv4, /31, /32 and bounded enumeration | Implemented; physical-network validation pending |
| NET-002 | Discovery | Host probes were launched without explicit concurrency limit and had no total per-host time budget | Medium | Candidate cap 1,024, bounded batches of 32, per-host TCP budget, bounded device enrichment and port scanning (16), cancellation propagation, bounded HTTP fingerprint response | Implemented on branch; CI/device validation pending |
| NET-003 | Network info | Wi-Fi connection data used deprecated WifiManager.connectionInfo and inferred security from Android version | Medium | Migrated to ConnectivityManager/LinkProperties and WifiInfo.currentSecurityType where available, with location-aware callback on API 31+ | Implemented on branch; physical-device validation pending |
| SEC-001 | Security audit | SecurityScreen received no nearby Wi-Fi list from navigation | High | Shared NetworkRepository StateFlow now supplies the same bounded scan results to nearby-network, security and channel-analysis screens; SecurityScreen can initiate the scan itself | Implemented on branch; CI/device validation pending |
| SEC-002 | Security score | A clean score could be shown without scan evidence | High | Score is now null until device-scan coverage, service-port scan and fresh nearby-Wi-Fi scan are all complete; missing evidence is explicitly disclosed | Implemented on branch; scoring calibration remains open |
| DATA-001 | Fingerprinting | HTTP response body was read without a byte limit | Medium | HTTP fingerprint now reads at most 32 KB, uses bounded timeouts, disables redirects/cache and disconnects in finally | Implemented on branch; CI/device validation pending |
| DATA-002 | Change tracking | ScanDiff kept one process-global previous map, not scoped to a network | Medium | ScanDiff now stores bounded per-network snapshots; switching networks no longer emits false disappearance events | Implemented on branch; regression tests added |
| DATA-003 | Widget | Widget scan started a coroutine from an exported BroadcastReceiver without goAsync() | Medium | Scan action moved to a dedicated non-exported WidgetScanReceiver; goAsync() is finished in finally | Resolved on working branch; CI/device validation pending |
| DATA-004 | Export | CSV fields were quoted but embedded double quotes were not escaped; spreadsheet formula injection was not neutralized | Medium | ExportHelper.exportDevicesAsCsv() | Resolved on working branch: RFC4180 quote escaping plus formula-prefix neutralization; regression tests added |
| SEC-005 | Components | Exported AppWidgetProvider accepted a custom scan action from arbitrary external broadcasts | Medium | ACTION_SCAN was included in the exported receiver intent filter | Resolved on working branch; scan action now targets an exported=false receiver |
| PERM-001 | Permissions | Android 12+ location permission requests should request ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION together | Medium | Three Wi-Fi scan screens requested fine only despite declaring coarse | Updated on working branch; API-level behavior validation pending |
| PERM-002 | Permissions | CHANGE_WIFI_MULTICAST_STATE was declared but no MulticastLock usage exists | Low | Manifest permission had no corresponding code use | Removed on working branch |
| WORK-001 | Background work | WatchWorker caught coroutine CancellationException as a generic Exception and returned retry | Medium | WatchWorker.doWork() generic catch | Resolved on working branch; cancellation now propagates |
| SEC-006 | Cleartext | Application globally enables cleartext traffic for local HTTP fingerprinting | Medium | Manifest previously allowed cleartext globally; local HTTP fingerprint used HttpURLConnection | Resolved on working branch: global cleartext is disabled; HTTP fingerprint uses a bounded raw socket only for RFC1918/link-local IPv4 targets, while HTTPS keeps platform TLS validation; physical-device validation pending
| NET-004 | Wi-Fi scan | Nearby-network scan could wait indefinitely for the results broadcast | High | Shared repository scanner now has a 10-second timeout, unregisters its receiver on completion/cancellation and distinguishes fresh results from cached results. Channel analyzer now consumes the shared bounded repository scanner | Partially resolved on branch |
| WIFI-001 | Channel analysis | Channel analysis omits 6 GHz and most DFS channels; 5 GHz scoring only counts exact-channel APs and ignores channel width/overlap | Medium | ChannelAnalyzer.analyze() fixed common5 list and frequency filters | Open |
| UI-001 | Topology | Canvas writes Compose state during drawing and renders inferred gateway-to-every-device edges as if they were topology | Medium | TopologyCanvas() mutates positions in Canvas draw scope and connects every ring node to gateway | Open |
| UI-002 | Design | Dashboard is a long stack of action rows; theme is dark-only and screens mostly use repeated generic cards | Medium | DashboardScreen.kt, Theme.kt and screen implementations | Open |
| SEC-003 | Security heuristics | Same SSID on another BSSID was treated as Evil Twin | High | Replaced with known-security-family mismatch signal; explicitly not proof of attack | Implemented on branch; device validation pending |
| SEC-004 | Security scoring | Current Wi-Fi encryption was not included in security findings | High | Current security mode now flags Open, WEP and legacy WPA where Android exposes enough evidence; generic PSK remains unknown rather than guessed | Implemented on branch; device validation pending |
| DATA-005 | Persistence | Known device IDs are only ever added to a DataStore set and are never pruned | Medium | Still accumulates; retention/pruning policy remains open | Open |
| DATA-006 | Persistence | Ping history is capped per IP, but old per-IP DataStore keys have no retention/cleanup policy | Low | PingHistoryStore.addSample() caps sample count but never removes unused keys | Open |
| MODEL-001 | Classification | Randomized/local MAC detection missed locally administered addresses whose low nibble is odd | Low | Device.isRandomMacAddress() now tests the IEEE U/L bit directly | Resolved on branch; tests added |
| UI-003 | Lock UX | Biometric lock could be enabled without checking authenticator availability; LockGate failed open if Activity was unavailable | Medium | SettingsScreen toggled the setting directly; LockGate set unlocked=true when context was not FragmentActivity | Partially resolved on working branch: settings checks biometric/PIN availability; LockGate now fails closed and surfaces prompt errors; lifecycle re-lock and physical-device validation remain open |
| DOC-001 | Docs | README feature list and roadmap are stale relative to source tree | Low | Compare README with current modules/screens | Open |

## External network and export privacy notes

- Opening the public-IP screen no longer triggers a network request. The user must press the explicit lookup button after seeing a disclosure.
- The primary ipapi.co endpoint receives the public IP and returns provider/approximate-location data. The fallback api.ipify.org endpoint is called only if the primary lookup fails and receives the same public source IP but returns only the address.
- Both lookups use HTTPS, reject redirects/non-2xx responses, cap bodies at 16 KiB, disconnect connections in finally and propagate coroutine cancellation.
- CSV export now escapes embedded quotes and neutralizes formula-like values before sharing. Reports and exports remain user-initiated share-sheet actions.

## Permission and receiver audit notes

- Wi-Fi scan screens request fine and coarse location together; Android 13+ additionally requests Nearby Wi-Fi Devices. Permission prompts remain tied to a user-initiated Wi-Fi scan.
- Notification permission is requested only when the user enables new-device notifications, not merely when background monitoring is enabled.
- The exported AppWidgetProvider handles system widget updates only. Its scan button targets a separate non-exported receiver, which uses `goAsync()` and always finishes the pending result.
- Removed the unused CHANGE_WIFI_MULTICAST_STATE manifest permission. Global cleartext allowance remains an open item because local HTTP fingerprinting currently depends on it.

## Latest Wi-Fi and security-service integration notes

- Nearby Wi-Fi scan results, completion state and last fresh-scan timestamp are owned by `NetworkRepository` and shared through StateFlow across the nearby-network, security and channel-analysis screens.
- A 10-second timeout and cancellation cleanup prevent a missing scan-results broadcast from leaving the UI waiting forever.
- Android cached scan results may be displayed, but only `EXTRA_RESULTS_UPDATED=true` advances the fresh-scan timestamp. A previous fresh scan is considered usable for five minutes.
- Security screen can request the necessary Wi-Fi permissions and launch the same repository scan without navigating away.
- Security scoring now also requires a separate bounded scan of Telnet, HTTP(S), SMB and RDP-related ports on discovered non-local hosts. Host concurrency is capped at four; per-host port concurrency remains capped at 16.
- Wi-Fi capability parsing distinguishes Open, WEP, WPA, WPA2, WPA3, WPA2/WPA3 transition and OWE; unknown capability strings are not assumed to be Open.
- Stock Android may throttle scans and redact identifiers; physical-device validation across API levels remains required.

## Definition of done

A phase is complete only when findings are recorded and resolved or explicitly accepted with rationale; relevant tests/checks pass; CI result is reviewed; documentation is updated; and no unrelated regression is knowingly left behind.

The roadmap is a living document. Reorder work when new evidence justifies it, while keeping the buildable baseline and user data safe.
