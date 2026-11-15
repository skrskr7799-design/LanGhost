# LanGhost — Engineering Roadmap & Audit Register

> Status: audit and stabilization in progress  
> Baseline branch: main  
> Baseline commit reviewed: c8e13219461fd1488ccbb83b4fa5ab721ba3eb62  
> Working branch: audit/initial-roadmap

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
- [ ] Reproduce the current APK build failure and capture the complete compiler output.
- [ ] Restore and verify the Gradle Wrapper (gradlew, gradlew.bat, wrapper JAR and properties).
- [ ] Verify Gradle, Android Gradle Plugin, Kotlin, Compose compiler/plugin, JDK, SDK and dependency compatibility.
- [ ] Fix all compile errors and warnings that indicate correctness or future compatibility risks.
- [ ] Make CI deterministic: least-privilege permissions, pinned/stable actions, clear triggers, build artifacts and useful failure logs.
- [ ] Separate pull-request/branch validation from tagged release publication.
- [ ] Verify both Debug and Release variants; define signing without committing secrets.
- [ ] Add baseline lint and test tasks.

### Phase 2 — Network engine
- [ ] Audit subnet/CIDR calculation, IPv4 edge cases and IPv6 support strategy.
- [ ] Review discovery methods (ARP, ICMP, TCP/UDP probes), timeouts, cancellation and false negatives.
- [ ] Bound concurrency, sockets, retries, scan duration and memory use.
- [ ] Review device identity, MAC randomization, duplicate merging and multi-AP/mesh behavior.
- [ ] Validate port scanning, service identification and banner/fingerprint handling.
- [ ] Make scan coverage and confidence visible to users.
- [ ] Test across different routers, subnet sizes, Android versions and network conditions.

### Phase 3 — Security, privacy and permissions
- [ ] Audit manifest permissions and request each permission only when needed.
- [ ] Review cleartext traffic, TLS, external endpoints, redirects, response-size limits and timeouts.
- [ ] Audit local data storage, backup/export paths, logs and sensitive identifiers.
- [ ] Verify biometric/app-lock behavior across lifecycle, process recreation and unavailable hardware.
- [ ] Review widget/receiver exposure and exported component requirements.
- [ ] Reassess security scoring and threat heuristics; avoid false certainty and misleading scores.
- [ ] Verify Wi-Fi threat detection against mesh networks, repeated SSIDs, roaming and legitimate configurations.
- [ ] Document what is and is not detectable on stock Android.

### Phase 4 — Architecture, data and background work
- [ ] Review repository boundaries, state ownership, ViewModels and dependency construction.
- [ ] Define durable models and persistence for devices, scan history, ping history and profiles.
- [ ] Bound history/cache growth and define retention/cleanup behavior.
- [ ] Review WorkManager constraints, scheduling, cancellation, notification permissions and battery impact.
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
- [ ] Add unit tests for pure network calculations, identity merging, scoring and diff logic.
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
| BUILD-002 | Build | Gradle Wrapper scripts/JAR absent from reviewed repository tree | High | Recursive tree at baseline commit | Open |
| BUILD-003 | CI | Legacy build workflow grants contents:write to the whole job and publishes a prerelease on every successful main push | Medium | .github/workflows/build-apk.yml | Open |
| TEST-001 | Testing | No unit or instrumentation test source files are present in the repository tree | High | Recursive tree contains no app/src/test or app/src/androidTest | Open |
| NET-001 | Discovery | IPv4 scan builds a /24-style prefix from the first three octets and derives only a host-number range from the netmask | High | NetworkRepository.scanDevices() and hostRangeFromNetmask(); non-/24 networks are not correctly enumerated | Open |
| NET-002 | Discovery | Host probes are launched for the full range without an explicit concurrency limit | Medium | NetworkRepository.discoverHosts() creates one IO coroutine per candidate host | Open |
| NET-003 | Network info | Wi-Fi connection data uses deprecated WifiManager.connectionInfo and reports security from Android version rather than negotiated network security | Medium | NetworkRepository.refreshNetworkInfo() | Open |
| SEC-001 | Security audit | SecurityScreen receives an empty nearby Wi-Fi list from current navigation, so same-SSID BSSID analysis has no input | High | SecurityScreen default nearbyNetworks = emptyList(); NavHost invokes SecurityScreen without argument | Open |
| SEC-002 | Security score | A clean score can be shown without proving that device port checks or Wi-Fi checks were completed | High | SecurityAudit.analyze() derives score only from findings and does not model scan coverage | Open |
| DATA-001 | Fingerprinting | HTTP response body is read fully before extracting the title, without a byte limit | Medium | DeviceProbe.httpFingerprint() uses readText() | Open |
| DATA-002 | Change tracking | ScanDiff keeps one process-global previous map, not persisted or scoped to a network | Medium | ScanDiff.previous | Open |
| DATA-003 | Widget | Widget scan starts a coroutine from BroadcastReceiver without goAsync() or a durable work handoff | Medium | ScanWidgetProvider.onReceive() | Open |
| DATA-004 | Export | CSV fields are quoted but embedded double quotes are not escaped | Medium | ExportHelper.exportDevicesAsCsv() | Open |
| NET-004 | Wi-Fi scan | Nearby-network and channel scans can suspend indefinitely if startScan succeeds but the results broadcast never arrives | High | scanNearbyNetworks() and scanWifi() use suspendCancellableCoroutine without a timeout around the broadcast wait | Open |
| WIFI-001 | Channel analysis | Channel analysis omits 6 GHz and most DFS channels; 5 GHz scoring only counts exact-channel APs and ignores channel width/overlap | Medium | ChannelAnalyzer.analyze() fixed common5 list and frequency filters | Open |
| UI-001 | Topology | Canvas writes Compose state during drawing and renders inferred gateway-to-every-device edges as if they were topology | Medium | TopologyCanvas() mutates positions in Canvas draw scope and connects every ring node to gateway | Open |
| UI-002 | Design | Dashboard is a long stack of action rows; theme is dark-only and screens mostly use repeated generic cards | Medium | DashboardScreen.kt, Theme.kt and screen implementations | Open |
| SEC-003 | Security heuristics | Same SSID on another BSSID is classified as an Evil Twin without considering mesh/roaming or matching security configuration | High | SecurityAudit.analyze() flags every different BSSID with the current SSID | Open |
| SEC-004 | Security scoring | Current Wi-Fi encryption is not included in the security score/findings | High | SecurityAudit.analyze() receives SSID/BSSID and nearby list, but not the current network security mode | Open |
| DATA-005 | Persistence | Known device IDs are only ever added to a DataStore set and are never pruned | Medium | DevicePreferences.saveKnownIds() unions new IDs with all previous IDs | Open |
| DATA-006 | Persistence | Ping history is capped per IP, but old per-IP DataStore keys have no retention/cleanup policy | Low | PingHistoryStore.addSample() caps sample count but never removes unused keys | Open |
| MODEL-001 | Classification | Randomized/local MAC detection misses locally administered addresses whose low nibble is odd | Low | Device.isRandomMacAddress() checks only 2, 6, A, E rather than testing bit 1 | Open |
| UI-003 | Lock UX | Biometric lock can be enabled without checking authenticator availability or handling prompt errors/cancellation with a recovery path | Medium | SettingsScreen toggles boolean directly; LockGate callback only handles success | Open |
| DOC-001 | Docs | README feature list and roadmap are stale relative to source tree | Low | Compare README with current modules/screens | Open |

## Definition of done

A phase is complete only when findings are recorded and resolved or explicitly accepted with rationale; relevant tests/checks pass; CI result is reviewed; documentation is updated; and no unrelated regression is knowingly left behind.

The roadmap is a living document. Reorder work when new evidence justifies it, while keeping the buildable baseline and user data safe.
