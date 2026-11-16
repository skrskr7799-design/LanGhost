# LanGhost — Engineering Roadmap & Audit Register

> Status: Phases 1–5 implementation and automated CI complete; Phase 6 physical-device validation remains open; Phase 7 Network Discovery Reliability IN PROGRESS
> Baseline branch: main
> Baseline commit: 6c36723497925e69d97081f944f82ff6bd4dc9e4 (PR #2 merged)
> Working branch: audit/phase-7-discovery-reliability

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
- [x] Verify the project with JDK 17, Android SDK 35, Gradle 8.9, Android Lint and unit-test tasks in CI. CI run 43697709829 on commit 8db7f710 completed successfully: lint, all 70 unit tests, Debug APK, Release APK and artifact upload.
- [x] Correct the confirmed baseline compile errors.
- [x] Replace the legacy workflow with branch/PR validation and tagged-release workflows; install Android command-line tools explicitly and upload Debug/Release APK artifacts.
- [x] Separate pull-request/branch validation from tagged release publication.
- [x] Confirm Debug and Release APK builds both pass on the latest validated working-branch commit; signing remains a release-readiness task.
- [x] Add baseline Android Lint and JUnit test tasks.

### Phase 2 — Network engine
- [x] Implement IPv4 CIDR calculation, contiguous-mask validation, /31 and /32 handling, bounded host enumeration; IPv6 strategy remains open.
- [x] Review discovery methods (ARP, TCP probes), timeouts, cancellation, false negatives, DNS/NetBIOS fan-out and port-scan concurrency at source level. Bounded orchestration, per-batch network-scope checkpoints, cancellation propagation and explicit coverage are implemented; physical-device validation remains open.
- [x] Bound discovery candidates to 1,024 and concurrent host probes to 32; add per-host time budget and explicit cancellation propagation. Socket-level cancellation remains bounded by connect timeout.
- [x] Scope device identities to a hashed logical network key (SSID + gateway + subnet), not BSSID; preserve legacy customizations and isolate scan diffs by network. Random-MAC rotation still cannot be correlated reliably.
- [x] Validate bounded TCP service-port scanning and explicit OPEN/CLOSED/FILTERED/UNKNOWN outcomes; exact port-set completeness is required for a trusted security score. Banner/service fingerprinting beyond port reachability remains a separate capability and is not inferred from port numbers.
- [x] Show checked/total IPv4 addresses, subnet and truncation warning; physical-device validation remains open.
- [ ] Test across different routers, subnet sizes, Android versions and network conditions (including mesh roaming and MAC randomization).

### Phase 2 — Network Engine: active review notes

Network Engine implementation CI #43703234977 passed on commit `ea60152598f3180aa23f9146a0f1c038f068c815`: Android Lint, unit tests, Debug APK, Release APK and artifact upload. Earlier regression in `NetworkIdentityTest.missingNetworkIdentifiersRemainUnknown` was fixed by ignoring zero IPv4 address/netmask placeholders. Additional validated hardening requires complete ARP entries, usable unicast MAC addresses, active-interface filtering, default-network observation across transports, and atomic network-scope publication/scan commits. Physical-device validation remains open.


The first source pass is complete; these are confirmed implementation characteristics and review targets, not yet defect declarations:

- `NetworkRepository` owns connectivity callbacks, network snapshots, Wi-Fi scanning, host discovery orchestration, device enrichment, persistence coordination and multiple UI-facing StateFlows. The first Phase 4 extraction moved bounded TCP probing into `NetworkProbeEngine`; continue separating cohesive responsibilities without changing externally visible behavior prematurely.
- Host discovery is deliberately bounded: at most 1,024 IPv4 candidates, 32 concurrent host probes, 16 concurrent enrichment/port probes, and four concurrent hosts during security service scans. Coverage records the checked/total count and truncation. Batch-boundary network-scope checkpoints now stop scans after a detected network change; two regression tests cover checkpoint cadence and cancellation.
- Discovery combines filtered `/proc/net/arp` entries with positive TCP-probe responses and the local/gateway addresses. A failed/filtered TCP probe is not proof that a host is offline; assess how the UI and history communicate uncertainty and disappearance.
- `DeviceProbe` wraps blocking UDP/TCP/HTTP work in coroutine timeouts and also sets socket/connection timeouts. Review cancellation latency and ensure cancellation closes active sockets rather than merely waiting for their bounded timeout.
- Logical network scope currently hashes SSID, gateway and IPv4 subnet. Validate collisions/unknown SSID behavior, roaming, mesh, VPN and simultaneous transports before treating this identity as durable.
- IPv6 is not yet part of host discovery. Keep IPv4-only coverage explicit until an authorized, bounded IPv6 strategy is designed.
- Manual device port scans now require a current device snapshot from the active logical network; result updates and enrichment are keyed by stable device identity and network scope. Device lookup no longer falls back to stale cached entries from another network.
- Real-device tests remain necessary for ARP visibility, Android permission redaction, Wi-Fi scan throttling, VPN/multi-network behavior, mesh roaming and MAC randomization.

Phase 2 implementation is complete. The NetworkRepository service-boundary refactor is now active in Phase 4. Real-device validation (ARP visibility, permission redaction, Wi-Fi throttling, VPN/multi-network, mesh roaming and MAC randomization) remains open in Phase 6.

### Phase 3 — Security, privacy and permissions
- [x] Audit manifest permissions and request each permission only when needed. Fine and coarse location are requested together for Wi-Fi scan flows; notification permission is requested only when enabling it. API-level device validation remains in Phase 6.
- [x] Review cleartext traffic, TLS, external endpoints, redirects, response-size limits and timeouts. Global cleartext is disabled; public-IP requests are HTTPS-only, size-bounded and cancellation-aware.
- [x] Audit local data storage, backup/export paths, logs and sensitive identifiers. Android backup is disabled; FileProvider is restricted to share artifacts; notifications omit device identifiers; history retention is bounded.
- [x] Verify biometric/app-lock behavior across lifecycle, process recreation and unavailable hardware at source level. Physical-device validation remains in Phase 6.
- [x] Review widget/receiver exposure and exported component requirements; widget updates remain on the exported AppWidgetProvider, while the scan action moved to a non-exported receiver with goAsync().
- [x] Reassess security scoring and threat heuristics; scores require fresh, network-scoped device coverage, a bounded service-port scan and a fresh nearby-Wi-Fi scan. Current Wi-Fi security is included; unknown encryption and incomplete evidence withhold the score.
- [x] Preserve per-port outcomes as OPEN/CLOSED/FILTERED/UNKNOWN; do not treat timeouts, routing errors or missing results as closed ports.
- [x] Require the exact policy port set for every discovered remote host; withhold the score when any result is filtered/unknown, missing/duplicated/mismatched, the subnet is truncated, or no remote target was discovered. Exclude the local handset from remote-host findings.
- [ ] Calibrate scoring weights and service-port policy against real router/device behavior; document false-positive/false-negative limitations.
- [x] Remove the assumption that a repeated SSID on another BSSID is an Evil Twin; compare only known, clearly different security families and label the result as a low-confidence configuration signal. Real-world mesh validation remains open.
- [x] Document what is and is not detectable on stock Android; physical-device validation remains in Phase 6.

### Phase 4 — Architecture, data and background work
- [x] Start the repository decomposition by extracting bounded TCP connect/probe mechanics into Android-independent `NetworkProbeEngine`; inject scan-timeout and network-scope validation policies rather than coupling the engine to Context or persistence. Regression tests cover open-port evidence, invalid/duplicate port filtering and cancellation on network-scope change.
- [x] Review repository boundaries, StateFlow ownership, ViewModels and dependency construction. Keep the singleton repository as the Android-facing state owner; keep subnet discovery, host probing, enrichment and lifecycle reconciliation in injected, platform-independent engines. ViewModels consume repository flows and own only screen state/actions; manual construction is retained because the app has no DI framework and the dependency graph remains small.
- [x] Define persistence boundaries: network observations, scan coverage and diffs remain process-local and expire rather than being restored as if current; user-authored device customizations and known identities are stored locally; ping history is bounded; named network profiles are persisted. This avoids presenting stale network evidence after process death.
- [x] Define retention/cleanup: known-device identities and their customization records are pruned together to a maximum of 512 retained identities; ping history retains 100 samples per IP, at most 64 IP keys and a 30-day window; network profiles are deduplicated and capped at 32 with bounded field lengths.
- [x] Preserve WorkManager cancellation propagation and request notification permission only when the notification feature is enabled. Scheduling constraints, battery impact and device validation remain open.
- [x] Move widget-triggered scans from short-lived BroadcastReceiver coroutine work into a unique persistent WidgetScanWorker; display an unknown state until a scan has completed.
- [x] Review error handling, offline behavior, concurrency safety and process-death recovery at source level. Network-scope commits remain atomic; cancelled work propagates cancellation; background scans use WorkManager; full repository/integration and physical-device validation remain in Phase 6.
- [x] Add structured diagnostic events with fixed component/event identifiers only; logs do not include SSIDs, IPs, MACs, URLs or raw payloads.
- [x] Complete full CI validation (Lint, unit tests, Debug APK and Release APK) on the final Phase 4 head before closing the phase. Run [#43714028759](https://github.com/skrskr7799-design/LanGhost/actions/runs/43714028759) passed all steps on final Phase 4 head `4cc6d383df5ceda30f171072c93820ec4d7ce55d`.

### Phase 5 — Full UI/UX and visual redesign (final-polish implementation)

- [x] Audit the screen inventory, route structure and primary/secondary navigation.
- [x] Establish a coherent dark-first design system: semantic colors, typography, spacing, shapes, borders and reusable Compose components.
- [x] Redesign the overview around live network state, scan coverage, meaningful metrics and explicit user-initiated actions.
- [x] Redesign Devices, device detail, Topology, Wi-Fi environment, Channel Analysis, Security Center, Tools, Utilities, Public IP, Network Profiles, Wake-on-LAN and Settings.
- [x] Implement adaptive phone and expanded layouts, including multi-pane workspaces for device inventory, topology, Wi-Fi, profiles, diagnostics and settings.
- [x] Preserve all existing routes and operational behavior while making network evidence, freshness, uncertainty and limitations visible.
- [x] Apply centralized IP/MAC/BSSID masking to UI, clipboard, exports and reports; retain raw values only for the underlying network operations.
- [x] Add shared loading, retryable error and stale/partial-data states; distinguish never-scanned from completed-empty results.
- [x] Correct TCP history language so service-port connection failures are not described as ICMP packet loss or host downtime.
- [x] Surface retryable persistence failures for device customizations instead of silently swallowing save errors.
- [x] Improve TalkBack semantics at the shared-component and app-navigation level: page/section headings, merged status badges, live operation-state announcements and selected-tab semantics.
- [x] Keep animation restrained and evidence-led; do not add decorative motion or external animation dependencies without a concrete interaction benefit.
- [ ] Complete hands-on TalkBack, contrast, touch-target and system-font-scaling checks on representative Android devices (tracked in Phase 6).
- [ ] Capture and review real phone/tablet screenshots and sign off visual behavior on physical devices (tracked in Phase 6).

Phase 5 implementation and automated validation are complete. Full CI run [#43815487552](https://github.com/skrskr7799-design/LanGhost/actions/runs/43815487552) passed on commit `f37ee9022ff97642764cc7bbc2d29bf3a2baa12e` (Android Lint, unit tests, Debug APK, Release APK and artifact upload). Device-only validation is deliberately not represented as complete by source review or CI; it remains an explicit Phase 6 acceptance task.
 
### Phase 6 — Automated and device testing
- [x] Add unit tests for pure network calculations, identity merging, scoring, diff, discovery orchestration, public-IP response validation/limits, CSV safety, retention bounds and evidence expiry (70 tests; all pass in CI run 43697709829 on commit 8db7f710).
- [ ] Add repository/integration tests for persistence, scanning orchestration and failure cases.
- [ ] Add Compose UI tests for critical flows and permission states.
- [ ] Run Android Lint and dependency/security checks in CI.
- [ ] Test on representative Android API levels and at least one physical device.
- [ ] Exercise real-network scenarios: no Wi-Fi, VPN active, mobile data, guest network, mesh, IPv6, denied permissions and interrupted scans.
- [ ] Add regression tests for every confirmed defect.

### Phase 7 — Network Discovery Reliability

**Status: IN PROGRESS**

**Trigger:** first physical-tablet audit (2026-11-16). In the user's home LAN, the scan reported only the tablet, router and server, while the user confirmed that a computer, TV and two phones were also connected. This is a reproducible product-level concern to investigate, not yet a proven single root cause.

**Goal:** make discovery materially more complete and explainable on stock Android, without claiming that silent, isolated or filtered devices are absent. Audit and improve the full path from active-network selection through candidate generation, ARP collection, reachability probes, endpoint merging, enrichment and UI publication.

#### Work packages

- [x] **7.1 Baseline and evidence audit**
  - [x] Trace the complete discovery pipeline and current Android permissions / network APIs.
  - [x] Verify active interface selection, IPv4 address/netmask/gateway calculation and candidate coverage.
  - [x] Inspect `/proc/net/arp` parsing and filtering (interface, flags, MAC validity, freshness, address scope).
  - [x] Review positive-evidence probes, timeout behavior, concurrency, cancellation and scan duration.
  - [x] Determine whether the current implementation can discover silent hosts; document why TCP-only reachability misses clients with no probed service.
  - [x] Record the observed tablet audit as a test scenario without assuming router-specific behavior.

- [ ] **7.2 Discovery strategy and evidence model**
  - Compare available stock-Android-compatible evidence sources (neighbor/ARP cache, bounded TCP probes, mDNS/NSD, SSDP and other safe LAN discovery signals).
  - Keep discovery restricted to the active user-selected/authorized IPv4 network and explicit bounded targets.
  - Distinguish direct observation, positive reachability, cached/stale evidence and inferred identity.
  - Never mark a host offline solely because one probe failed; preserve UNKNOWN / FILTERED / NOT OBSERVED semantics.
  - Make scan coverage, skipped ranges, unsupported methods and platform limitations visible.

- [ ] **7.3 Engine implementation and resilience**
  - Refactor `NetworkDiscoveryEngine`, `HostDiscovery`, `DeviceProbe` and repository orchestration where evidence supports it.
  - Preserve bounded candidate counts, bounded concurrency, network-scope checkpoints and cancellation.
  - Avoid duplicate devices when multiple discovery methods report the same endpoint.
  - Prevent stale results from crossing network changes; retain privacy-safe diagnostics.
  - Add deterministic tests for ARP parsing, interface filtering, candidate coverage, evidence merging, silent hosts, timeouts, cancellation, duplicate observations and network switching.

- [ ] **7.4 Physical-network verification**
  - Re-test on the user's tablet and home LAN with known clients: tablet, router, server, computer, TV, user's phone and brother's phone.
  - Compare expected devices against observed devices and record per-device evidence / reason for non-detection.
  - Test different host response profiles (ping disabled, no common TCP ports, sleeping, Wi-Fi client isolation where available).
  - Verify no false claim of complete inventory when Android/router visibility is restricted.

#### Acceptance criteria

- [ ] Discovery pipeline and its limitations are documented from source, not guessed.
- [ ] Every candidate/observation has traceable evidence and a clear distinction between observed, inferred, stale and unknown.
- [ ] Regression tests cover each confirmed failure and the supported discovery methods.
- [ ] Scan bounds, cancellation, network-scope isolation and privacy guarantees remain intact.
- [ ] Full CI (Android Lint, unit tests, Debug APK, Release APK and artifact upload) passes on the final Phase 7 head.
- [ ] Tablet/home-LAN verification is completed and results are recorded. If Android or router restrictions prevent complete visibility, the UI must disclose that limitation honestly.
- [ ] README and roadmap reflect the actual implementation and validation status.

**Exit rule:** Phase 7 is not closed by source review or CI alone. It closes only after all implementation criteria pass and the known-device tablet scenario has been re-tested. Any platform limitation that cannot be removed must be explicitly documented and surfaced in the product.

### Phase 8 — Device Intelligence

- [ ] Improve vendor identification using available MAC/OUI evidence; explicitly label randomized/local MAC addresses and unknown vendors.
- [ ] Improve device-type classification using multiple independent signals and confidence/evidence.
- [ ] Improve OS hints only where evidence supports them; never infer a precise OS from a port number alone.
- [ ] Keep observed facts separate from heuristics and show confidence / source in device details.
- [ ] Add deterministic tests for conflicting, missing and weak signals.

### Phase 9 — Wi-Fi Analyzer Actions

- [ ] Turn nearby access-point inventory into an actionable radio-analysis workspace.
- [ ] Add detailed network inspection: band, channel, width where available, RSSI, security and freshness.
- [ ] Improve channel overlap / congestion visualization within Android scan-data limitations.
- [ ] Add evidence-based recommendations and explain uncertainty; do not claim measured interference from scan results alone.
- [ ] Keep operations limited to information Android exposes; no interaction with or probing of neighboring networks.

### Phase 10 — Device Actions and Wake-on-LAN Integration

- [ ] Add an explicit action on eligible discovered device cards/details to create a Wake-on-LAN profile.
- [ ] Pre-fill MAC and available network parameters; require user confirmation before saving.
- [ ] Handle missing, randomized, multicast or otherwise unusable MAC addresses with a clear unavailable state.
- [ ] Preserve explicit user control over sending a Magic Packet and explain that packet transmission does not prove the device powered on.
- [ ] Add regression tests for eligibility, parameter mapping and privacy masking.

### Phase 11 — UX Stabilization

- [ ] Stabilize Ping/TCP diagnostic result layout so updates do not cause visible jumps.
- [ ] Audit navigation/back-stack behavior and ensure Overview is consistently reachable.
- [ ] Review operation feedback, empty/loading/error states and adaptive phone/tablet/landscape behavior.
- [ ] Add focused Compose UI tests for the corrected flows.

### Phase 12 — Release readiness

- [ ] Final performance, battery, accessibility and privacy review.
- [ ] Verify versioning, release notes, reproducible CI outputs and artifact retention.
- [ ] Configure secure release signing and document key handling.
- [ ] Update README, permissions explanation, feature list, screenshots and troubleshooting.
- [ ] Verify clean clone → build → install → first-run flow.
- [ ] Publish only after explicit release approval.

## Initial verified baseline observations

These are repository observations, not a complete audit verdict:

- The original audit baseline was `c8e13219461fd1488ccbb83b4fa5ab721ba3eb62`. The current main baseline for Phase 5 is merge commit `e581fd0523aa90fd57a9338ac8eaae4e5a17408f` (PR #1).
- The baseline GitHub Actions run #33 (43682690627) failed during Kotlin compilation. The compiler output identified invalid named Modifier arguments in LockGate.kt and SecurityScreen.kt. These were corrected on this branch; validation runs #2 (43683564098) and #3 (43683602775) both completed successfully, including Debug APK artifact upload.
- At the original baseline, the Gradle Wrapper scripts/JAR were absent; the Wrapper has since been restored and is part of current main.
- The README still describes several features already present in the source tree as roadmap items. Documentation needs reconciliation.
- Release build/signing and test coverage require a dedicated review; do not infer readiness from the existence of a release build type.

## Audit register

Use one entry per finding. Do not mark a suspected issue as confirmed without source evidence, a reproducible test, or a reliable platform constraint.

| ID | Area | Finding | Severity | Evidence / reproduction | Status |
|---|---|---|---|---|---|
| BUILD-001 | Build | Baseline Kotlin compilation failed on invalid Compose named arguments | Blocker | Baseline Actions run #33; corrected LockGate.kt and SecurityScreen.kt; validation runs #2 and #3 succeeded | Resolved on audit branch; verify again after later code changes |
| BUILD-002 | Build | Gradle Wrapper scripts/JAR absent from reviewed repository tree | High | Recursive tree at baseline commit; official Wrapper files and Gradle 8.9 distribution checksum added and verified in CI | Resolved on working branch; main unchanged |
| BUILD-003 | CI | Legacy build workflow grants contents:write to the whole job and publishes a prerelease on every successful main push | Medium | Replaced with branch/PR build workflow and tag-only release workflow; verify permissions and release behavior during final CI review | Resolved on working branch; main unchanged |
| TEST-001 | Testing | No unit or instrumentation test source files are present in the repository tree | High | Added 70 JUnit tests across network calculations, discovery, security, identity, export, public-IP response bounds and data-retention policies; all 70 passed in CI run 43697709829 on commit 8db7f710 | Resolved on working branch; Compose/integration coverage remains open |
| NET-001 | Discovery | IPv4 scan built a /24-style prefix from the first three octets | High | Ipv4Subnet validates masks and handles unsigned IPv4, /31, /32 and bounded enumeration | Implemented; physical-network validation pending |
| NET-002 | Discovery | Host probes were launched without explicit concurrency limit and had no total per-host time budget | Medium | Candidate cap 1,024, bounded batches of 32, per-host TCP budget, bounded device enrichment and port scanning (16), cancellation propagation, bounded HTTP fingerprint response | Implemented on branch; CI/device validation pending |
| NET-003 | Network info | Wi-Fi connection data used deprecated WifiManager.connectionInfo and inferred security from Android version | Medium | Migrated to ConnectivityManager/LinkProperties and WifiInfo.currentSecurityType where available, with location-aware callback on API 31+ | Implemented; CI passed on 8db7f710; physical-device validation pending |
| SEC-001 | Security audit | SecurityScreen received no nearby Wi-Fi list from navigation | High | Shared NetworkRepository StateFlow now supplies the same bounded scan results to nearby-network, security and channel-analysis screens; SecurityScreen can initiate the scan itself | Implemented on branch; CI/device validation pending |
| SEC-002 | Security score | A clean score could be shown without complete/fresh scan evidence, current-network scope or a known Wi-Fi encryption mode | High | Score requires current-network device coverage, service-port scan, nearby-Wi-Fi scan and known encryption mode; all evidence expires after five minutes | Implemented on branch; CI/device validation pending |
| DATA-001 | Fingerprinting | HTTP response body was read without a byte limit | Medium | HTTP fingerprint now reads at most 32 KB, uses bounded timeouts, disables redirects/cache and disconnects in finally | Implemented on branch; CI/device validation pending |
| DATA-002 | Change tracking | ScanDiff kept one process-global previous map, not scoped to a network | Medium | ScanDiff now stores bounded per-network snapshots; switching networks no longer emits false disappearance events | Implemented on branch; regression tests added |
| DATA-003 | Widget | Widget scan execution could outlive the short BroadcastReceiver execution window | Medium | WidgetScanReceiver now enqueues a unique persistent WidgetScanWorker; scan and widget refresh complete in WorkManager rather than a receiver-owned coroutine | Implemented on working branch; CI/device validation pending |
| DATA-004 | Export | CSV fields were quoted but embedded double quotes were not escaped; spreadsheet formula injection was not neutralized | Medium | ExportHelper.exportDevicesAsCsv() | Resolved on working branch: RFC4180 quote escaping plus formula-prefix neutralization; regression tests added |
| SEC-005 | Components | Exported AppWidgetProvider accepted a custom scan action from arbitrary external broadcasts | Medium | ACTION_SCAN was included in the exported receiver intent filter | Resolved on working branch; scan action now targets an exported=false receiver |
| PERM-001 | Permissions | Android 12+ location permission requests should request ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION together | Medium | Three Wi-Fi scan screens requested fine only despite declaring coarse | Updated on working branch; API-level behavior validation pending |
| PERM-002 | Permissions | Multicast reception for foreground mDNS discovery requires a lock on older Android releases | Low | Phase 7 NSD adapter uses WifiManager.MulticastLock only on Android 12 and Android 13 before T extension 7; lock is released in finally | Reintroduced narrowly for Phase 7; no lock held outside the bounded foreground scan |
| PERM-003 | Notifications | Enabling background monitoring could trigger the Android notification permission prompt even though monitoring and notifications are separate preferences | Low | SettingsScreen watch-mode callback checked the notification preference and launched POST_NOTIFICATIONS | Resolved on work/data-architecture: monitoring no longer requests notification permission; new installs default notifications off; the permission is requested only from the explicit notification opt-in control; default covered by unit test |
| TOOLS-001 | DNS utility | DNS lookup could swallow coroutine cancellation and had no explicit lookup deadline or input bound | Medium | UtilitiesScreen.runDns() caught generic Exception around blocking InetAddress.getAllByName() | Resolved on work/data-architecture: shared interruptible DNS resolution has a four-second deadline, host input is bounded, output is capped at 16 addresses, and CancellationException propagates |

| WORK-001 | Background work | WatchWorker caught coroutine CancellationException as a generic Exception and returned retry | Medium | WatchWorker.doWork() generic catch | Resolved on working branch; cancellation now propagates |
| SEC-006 | Cleartext | Application globally enabled cleartext traffic for local HTTP fingerprinting | Medium | Manifest and DeviceProbe source review | Resolved on working branch: global cleartext is disabled; HTTP fingerprint uses a bounded raw socket only for RFC1918/link-local IPv4 targets, while HTTPS keeps platform TLS validation; physical-device validation pending |
| PRIV-001 | External lookup | Public IP and approximate-location data were requested automatically when opening the public-IP screen | Medium | PublicIpScreen launched PublicIpLookup from LaunchedEffect(Unit) | Resolved on working branch: lookup now requires an explicit tap after a disclosure; HTTPS requests disable redirects, cap responses at 16 KiB and disconnect reliably
| PRIV-002 | Privacy UX | The “hide MAC and IP” preference was exposed but had no consumers | Medium | AppLockSettings and SettingsScreen exposed a setting without masking UI/export values | Mitigated: nonfunctional toggle and unused API removed; consistent privacy masking is explicitly deferred to Phase 5 |
| NET-014 | Scan isolation | A network change during a bounded scan could leave work probing the previous subnet; manual device scans and cache fallback were not consistently bound to the active network | High | Added a network refresh/scope checkpoint before every bounded batch; manual device port scans require the active device snapshot, commit by stable ID + scope, and device lookup no longer returns another network's cached device | Implemented on work/network-engine; regression tests added; latest CI pending |
| NET-015 | ARP evidence | Incomplete neighbor entries could be accepted if a MAC-shaped value was present | Medium | Parser now requires ATF_COM (0x2), a non-zero unicast-shaped MAC value and the active interface; regression fixture includes an incomplete entry with a syntactically valid MAC | Implemented on work/network-engine; CI pending |
| NET-016 | Network identity | Delimiter-based scope material could collide when identifiers contained separators; zero-address placeholders could produce a non-unknown /0 scope | Medium | Scope material now uses length-prefixed fields; legacy hash remains available for customization migration; missing/zero IPv4 address or netmask no longer contributes a subnet | Implemented on work/network-engine; CI pending |
| NET-017 | Connectivity | Wi-Fi-only callback registration could miss default-route transitions to Ethernet or VPN | Medium | Replaced transport-filtered registration with registerDefaultNetworkCallback (minSdk 26); callback now observes the system default network across transports | Implemented on work/network-engine; CI/device validation pending |
| NET-018 | Async scan evidence | Results from a stable device identity could still be stale after its endpoint IP changed | Medium | Port-scan and enrichment commits now validate stable ID, network scope and endpoint IP; security-port results merge only against the scanned endpoint | Implemented on work/network-engine; CI pending |
| DATA-007 | HTTP fingerprint | Per-read timeout renewal could allow a slow peer to extend the total response read indefinitely | Medium | HTTP fingerprint body reads now share one monotonic total deadline across chunks, with remaining timeout applied to each blocking read | Implemented on work/network-engine; CI pending |
| NET-004 | Wi-Fi scan | Nearby-network scan could wait indefinitely for the results broadcast | High | Shared repository scanner now has a 10-second timeout, unregisters its receiver on completion/cancellation and distinguishes fresh results from cached results. Channel analyzer now consumes the shared bounded repository scanner | Implemented; CI passed on 8db7f710; physical-device validation pending |
| WIFI-001 | Channel analysis | Channel analysis omits 6 GHz and most DFS channels; 5 GHz scoring only counts exact-channel APs and ignores channel width/overlap | Medium | ChannelAnalyzer.analyze() fixed common5 list and frequency filters | Open |
| UI-001 | Topology | Canvas wrote Compose state during drawing and presented inferred gateway-to-client grouping as confirmed topology | Medium | Replaced with deterministic draw-time positions, no state mutation during Canvas drawing, and an explicit logical-grouping disclaimer; no gateway means no inferred edges | Resolved in topology screen pass; physical topology discovery remains a future capability |
| UI-002 | Design | Dashboard is a long stack of action rows; theme is dark-only and screens mostly use repeated generic cards | Medium | DashboardScreen.kt, Theme.kt and screen implementations | Open |
| SEC-003 | Security heuristics | Same SSID on another BSSID was treated as Evil Twin | High | Replaced with known-security-family mismatch signal; explicitly not proof of attack | Implemented on branch; device validation pending |
| SEC-004 | Security scoring | Current Wi-Fi encryption was not included in security findings | High | Current security mode now flags Open, WEP and legacy WPA where Android exposes enough evidence; generic PSK remains unknown rather than guessed | Implemented on branch; device validation pending |
| DATA-005 | Persistence | Known device IDs and per-device customization keys could accumulate without a coordinated retention policy | Medium | DevicePreferences.saveKnownIds() previously unioned IDs forever; customization entries used separate DataStore keys | Resolved on working branch: ordered index is capped at 512 recently seen identities and stale customization keys are pruned in the same atomic DataStore edit; regression test added |
| DATA-006 | Persistence | Ping history was capped per IP, but per-IP DataStore keys had no retention/cleanup policy | Low | PingHistoryStore.addSample() | Resolved in source: 100 samples per IP, a 30-day window and a maximum of 64 IP histories; expired samples are deleted on write and hidden on read |
| DATA-007 | Persistence | Network profiles had no upper bound, malformed entries were accepted and active IDs could refer to missing profiles | Low | NetworkProfiles.add(), parse() and setActive() | Resolved on working branch: deduplicate/validate entries, cap at 32, bound stored field lengths and reject dangling active IDs; regression test added |
| DATA-008 | Concurrency | AppSettings.getOnce() converted coroutine cancellation into default settings | Medium | Broad Exception catch around Flow.first() swallowed CancellationException | Resolved on working branch: cancellation is rethrown and covered by a unit test |
| UI-004 | Privacy | LockGate could render content using an initial false setting before DataStore finished loading the persisted biometric-lock state | High | collectAsState(initial = false) raced with a separate first() read | Resolved on working branch: nullable loading state blocks content until the persisted setting is observed; fail-closed prompt behavior retained |
| MODEL-001 | Classification | Randomized/local MAC detection missed locally administered addresses whose low nibble is odd | Low | Device.isRandomMacAddress() now tests the IEEE U/L bit directly | Resolved on branch; tests added |
| UI-003 | Lock UX | Biometric lock could be enabled without checking authenticator availability; LockGate failed open if Activity was unavailable and did not re-lock on background | Medium | SettingsScreen toggled the setting directly; LockGate set unlocked=true when context was not FragmentActivity and kept unlocked state in memory | Implemented on working branch: settings checks biometric/PIN availability; LockGate fails closed, reports prompt errors, observes the setting and re-locks on Activity ON_STOP; physical-device validation remains open |
| SEC-007 | External lookup | Public-IP response values were not validated as IP literals and blocking requests could delay cancellation | Low | PublicIpInfo.kt response parsing and blocking HttpURLConnection calls | Resolved in source: validates IPv4/IPv6 literals, uses interruptible IO, bounded response size, request timeouts, no redirects and disconnect in finally |
| SEC-008 | File sharing | FileProvider exposed the entire application cache directory to URI-granted recipients | Medium | res/xml/file_paths.xml exposed cache path "." | Resolved in source: FileProvider exposes only cache/shared; generated share files older than 24 hours are pruned |
| SEC-009 | Notifications | New-device notifications exposed device names and local IP addresses on lock screens | Low | NotificationHelper included device name and IP in notification text | Resolved in source: notifications contain generic network-change text only and use private lock-screen visibility |
| SEC-010 | Scan evidence | Cancelled discovery could publish non-zero coverage before enrichment and persistence completed | Medium | NetworkRepository.scanDevices() updated scanCoverage before bounded enrichment completed | Resolved in source: coverage is published only after discovery, enrichment and persistence finish; cancelled scans remain incomplete |
| SEC-011 | Network scope | Device, port and nearby-Wi-Fi evidence could remain attached to a previous network after switching SSID/subnet | High | NetworkRepository retained scan state across NetworkInfo changes; SecurityAudit checked only that coverage was non-zero | Resolved in source: logical network changes invalidate device/port/Wi-Fi evidence; scans re-check scope before publishing; SecurityAudit rejects coverage from a different scope; regression test added |
| SEC-012 | Evidence freshness | Device, service-port and nearby-Wi-Fi findings could remain visible after their evidence became stale | Medium | SecurityAudit trusted completion booleans without checking scan timestamps | Resolved in source: all three evidence sources expire after five minutes; stale port/AP findings are suppressed and the SecurityScreen refreshes freshness state every 15 seconds |
| SEC-013 | Wi-Fi security evidence | Unknown/unavailable current Wi-Fi encryption could be treated as a clean signal | Medium | SecurityAudit previously had no finding or score gate for an unrecognized security mode | Resolved in source: unknown mode produces an explicit informational finding and withholds the overall score; regression test added |
| SEC-014 | Port evidence | A port timeout, routing failure, incomplete port set or unscanned local/remote target could be mistaken for a clean service audit | High | NetworkRepository.scanSecurityPorts() previously retained only open ports; SecurityAudit used a hard-coded result count and reported local-device evidence although the local handset is not scanned | Resolved on work/network-engine: explicit OPEN/CLOSED/FILTERED/UNKNOWN outcomes; shared exact port policy; score withheld for filtered/unknown/missing/duplicate/mismatched evidence, truncated coverage or zero discovered remote targets; local handset excluded; regression tests added; production findings use fresh positive port evidence rather than legacy cached port lists |
| SEC-015 | Service identification | TCP connect success was presented as proof of Telnet, SMB, RDP or HTTP service | Medium | Port-number findings now say the TCP port is open and name the conventional protocol only as a hint; details explicitly state that no protocol fingerprint/banner verification was performed. TCP/23 severity is reduced to Medium until service identity is confirmed | Implemented on work/network-engine; regression tests added; active service fingerprinting remains a separate future capability |
| DOC-001 | Docs | README did not describe the implemented feature set or its operational limitations | Low | README now documents current capabilities, external requests, privacy boundaries, platform limitations, build and validation workflow | Resolved on work/data-architecture |

## Persistence and app-lock notes

- Known-device ID history is migrated from the legacy set to an ordered JSON index capped at 512 recently seen identities. Per-device customization keys are pruned atomically against that retained identity set, so storage remains bounded; customizations for identities that age out of the 512-entry window are removed.
- Ping history retains at most 100 samples per IP, a 30-day window and 64 IP histories; expired samples are deleted from active histories and expired/least-recently-used keys are pruned on each write.
- Network profiles are deduplicated, limited to 32 entries and bounded by field length; an active profile must exist in the stored profile list.
- Full device snapshots, scan coverage and scan diffs are intentionally not restored from disk: after process death they must be rediscovered rather than displayed as current. Persistent user customizations, known identity history, ping samples and profiles are separate from transient scan evidence.
- App lock checks authenticator availability before enabling, fails closed if the host Activity or prompt is unavailable, waits for the persisted setting before rendering protected content, reacts to setting changes and re-locks when the Activity stops. Device credential / biometric lifecycle behavior still needs physical-device validation.

## External network and export privacy notes

- Opening the public-IP screen no longer triggers a network request. The user must press the explicit lookup button after seeing a disclosure.
- The primary ipapi.co endpoint receives the public IP and returns provider/approximate-location data. The fallback api.ipify.org endpoint is called only if the primary lookup fails and receives the same public source IP but returns only the address.
- Both lookups use HTTPS, reject redirects/non-2xx responses, cap bodies at 16 KiB, disconnect connections in finally and propagate coroutine cancellation. Returned IP values are validated as IPv4/IPv6 literals without resolving hostnames.
- CSV export escapes embedded quotes, neutralizes formula-like values after BOM/leading whitespace and preserves commas inside quoted cells. Reports and exports remain user-initiated share-sheet actions.
- FileProvider exposes only the dedicated cache/shared directory; old generated share artifacts are deleted after 24 hours.
- New-device notifications contain no device name, IP or MAC and use private lock-screen visibility.

## Permission and receiver audit notes

- Wi-Fi scan screens request fine and coarse location together; Android 13+ additionally requests Nearby Wi-Fi Devices. Permission prompts remain tied to a user-initiated Wi-Fi scan.
- Notification permission is requested only when the user enables new-device notifications, not merely when background monitoring is enabled.
- The exported AppWidgetProvider handles system widget updates only. Its scan button targets a separate non-exported receiver, which immediately enqueues a unique WorkManager request; the persistent worker performs discovery and refreshes all widget instances. The widget shows “no data” until a successful scan timestamp exists.
- Removed the unused CHANGE_WIFI_MULTICAST_STATE manifest permission. Global cleartext traffic is disabled; local HTTP fingerprinting uses a bounded raw TCP socket only for RFC1918/link-local IPv4 targets. HTTPS fingerprinting retains platform certificate validation.

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


## Security and network-engine validation — 2026-11-15

Network Engine source implementation is complete on `work/network-engine`; its dedicated CI run #43703234977 passed on commit `ea60152598f3180aa23f9146a0f1c038f068c815`. Security Engine evidence hardening is in progress on the same working branch; the latest full CI result must be recorded after the current changes land.

Security scores remain network-scoped and time-bounded. Device coverage is published only after discovery, enrichment and persistence complete; cancelled/failed runs, evidence from another network, truncated coverage, stale scans, unknown Wi-Fi encryption, incomplete per-port evidence and missing remote targets cannot produce a score. Port outcomes preserve OPEN/CLOSED/FILTERED/UNKNOWN distinctions; a timeout or routing error is not proof that a port is closed. Only the exact configured audit-port set is accepted for each discovered remote host. Local-handset ports are not presented as remotely audited evidence. Production security findings are derived only from positive OPEN outcomes in the fresh per-port evidence; legacy cached port lists cannot create a new finding.

This is source-level and automated-test validation, not a substitute for Phase 6 device validation: Android Wi-Fi scan throttling, permission behavior, biometric lifecycle behavior, VPN routing, ARP visibility and router-specific service visibility still require physical-device testing. User-authored device customizations are intentionally retained locally rather than silently discarded. The old sensitive-data toggle was removed because it did not mask any UI; consistent masking is planned for Phase 5.

## Security Engine protocol-identification boundary

- The current service audit performs bounded TCP connection attempts against a fixed port policy. A successful TCP handshake establishes that the port accepted a connection at scan time; it does not identify the application protocol.
- Findings therefore name the observed TCP port and describe Telnet, SMB or RDP only as the conventional service commonly associated with that port. The gateway port-80 finding no longer claims that an HTTP service or administration page was identified.
- TCP/23 is Medium severity until a protocol-aware check confirms the service. This avoids treating an unverified port-number heuristic as proof of cleartext Telnet.
- Banner grabbing, protocol negotiation and service fingerprinting are not implemented in this phase. They require a separately scoped, bounded and privacy-conscious design, including strict time/byte limits and safe handling of arbitrary service responses.


## Security Engine completion checkpoint — 2026-11-15

The prior evidence-hardening commit `4f4b92927bc7225ae5d3b362a1b619e0804b67d5` passed full CI run [#43705292774](https://github.com/skrskr7799-design/LanGhost/actions/runs/43705292774): Android Lint, unit tests, Debug APK, Release APK and artifact upload all succeeded. The run SHA matched `work/network-engine` HEAD at verification time. The protocol-identification boundary above is the final planned Security Engine source change; final phase completion remains conditional on the next full CI run.


## Phase 3 completion and Phase 4 kickoff — 2026-11-15

Security Engine implementation and automated validation are complete on the predecessor branch. Full CI run [#43705847175](https://github.com/skrskr7799-design/LanGhost/actions/runs/43705847175) passed on commit `5a2283d9aeda9ea97ba37bfe521164fdc01e94d5`: Android Lint, unit tests, Debug APK, Release APK and artifact upload all succeeded. The run SHA matched the `work/network-engine` branch HEAD at verification time. This is implementation/CI completion, not physical-device validation; router behavior, Android permission redaction, Wi-Fi throttling, VPN/multi-network and mesh roaming remain open for Phase 6.

Phase 4 is now active on `work/data-architecture`, branched from that validated commit. The first extraction introduced `NetworkProbeEngine`, which owns bounded host TCP reachability probes, manual TCP port scans and structured security-port evidence scans. Scan timeout and active-network-scope checks are injected, preserving repository policy and cancellation boundaries while removing raw socket mechanics from `NetworkRepository`. Added focused local-loopback tests for positive open-port evidence, bounded/validated port input and scope-change cancellation. That first extraction passed full CI run [#43706850613](https://github.com/skrskr7799-design/LanGhost/actions/runs/43706850613) on commit `290bf51cf292b5073e7ee961f203916fba672099`: Android Lint, unit tests, Debug APK, Release APK and artifact upload all succeeded. Continue with discovery orchestration and state/persistence boundaries after each extraction is independently validated.


## Phase 4 — Wi-Fi scan coordination extraction

The `WifiScanCoordinator` extraction is implemented on `work/data-architecture`. Android receiver registration, scan start, cached-result retrieval, broadcast completion, timeout fallback and cancellation cleanup now live behind a small `WifiScanPlatform` boundary. `NetworkRepository` retains responsibility for network-scope validation and atomic publication of nearby-network evidence. Freshness semantics are unchanged: cached results may be displayed, but only `EXTRA_RESULTS_UPDATED=true` advances the fresh-scan timestamp.

Focused coordinator tests cover successful broadcast completion, duplicate callbacks, rejected scan fallback, timeout fallback and cancellation cleanup. The extraction is pending full CI validation; physical-device checks for Android permission behavior, scan throttling and receiver lifecycle remain part of Phase 6.


## Phase 4 — Network discovery orchestration extraction

The next Phase 4 boundary is implemented on `work/data-architecture`. `NetworkDiscoveryEngine` now owns subnet candidate generation, bounded positive-evidence host probing, ARP/subnet filtering, endpoint merging (ARP + discovered + local + gateway) and `ScanCoverage` calculation. The Android repository remains responsible for reading the active-interface ARP table, constructing/enriching `Device` models, user customization, cache and atomic network-scoped publication. The engine receives probe and network-scope checkpoint callbacks, preserving cancellation and network-switch isolation without Android dependencies.

Regression tests cover evidence merging and subnet isolation, candidate limits and truncated coverage, invalid subnet handling, and scope-change cancellation between bounded batches. Full CI validation is required before this extraction is considered complete.


## Phase 4 — Device enrichment extraction

Device snapshot construction has been moved into the platform-independent `DeviceEnrichmentEngine`. It receives injected customization loading, hostname resolution and vendor lookup; the repository remains responsible for choosing the current-scope cached endpoint and stable-ID snapshot, bounding concurrency, and committing results atomically. The engine preserves network-scoped stable IDs, legacy customization lookup order, randomized-MAC handling, first-seen timestamps, cached hostname/vendor fallback, device classification, user labels and notes, tags, port inventory, security audit evidence and HTTP fingerprint. Unit tests cover customization restoration and evidence preservation, randomized MAC behavior and cached fallback, and scope-key isolation. CI validation pending.


## Phase 4 — Device enrichment validation checkpoint — 2026-11-15

The `DeviceEnrichmentEngine` extraction and its network-scoped customization migration tests passed full CI run [#43710316789](https://github.com/skrskr7799-design/LanGhost/actions/runs/43710316789) on commit `653d4995485039640ed05ed1a52036370ae47005`. Android Lint, unit tests, Debug APK, Release APK and APK artifact upload all succeeded. Customization migration is restricted to the previous network-scoped key format; unscoped MAC/IP/BSSID fallbacks are deliberately excluded to prevent preferences from leaking between unrelated networks. The next Phase 4 boundary is the pure device lifecycle/reconciliation logic currently embedded in `NetworkRepository.scanDevices()`.


## Phase 4 — Device lifecycle reconciliation checkpoint — 2026-11-15

The `DeviceLifecycleEngine` extraction passed full CI run [#43710995249](https://github.com/skrskr7799-design/LanGhost/actions/runs/43710995249) on commit `4681cb013c6700c2a501b8d81a770dfe1ba97721`. Android Lint, unit tests, Debug APK, Release APK and artifact upload all succeeded. The pure engine now owns deterministic reconciliation of discovered snapshots and cached devices: it retains same-network offline devices within the existing 24-hour window, prefers the newest discovered snapshot per stable identity, rejects other network scopes, resolves duplicate identities deterministically and preserves the existing ordering policy. The repository retains cache access, persistence and atomic network-scope publication. Regression tests cover retention, online replacement, scope isolation, deterministic deduplication and expiry.

Phase 4 continues with a focused audit of remaining `NetworkRepository` responsibilities and persistence/state boundaries. Do not start the Phase 5 visual redesign until the architecture audit, tests, documentation and full CI are complete.


## Phase 4 — final architecture review checkpoint — 2026-11-15

The final source pass keeps NetworkRepository as the Android-facing coordinator and single publisher of network/device StateFlows. NetworkProbeEngine, NetworkDiscoveryEngine, DeviceEnrichmentEngine and DeviceLifecycleEngine own cohesive algorithms and accept policies/callbacks rather than Android Context or persistence dependencies. The remaining repository responsibilities—active-network snapshot validation, ARP table access, bounded cache, DataStore coordination and atomic StateFlow commits—belong at the Android/data boundary; further splitting would create artificial modules without reducing coupling.

The pass also fixed a fail-open rendering race in LockGate: protected content is now withheld until the persisted biometric setting is observed. AppSettings.getOnce() now propagates cancellation instead of treating it as a storage fallback. Device customization records now share the 512-identity retention boundary, network profiles are capped at 32, and widget scans are executed by persistent WorkManager work rather than a receiver-owned coroutine. Added regression coverage for identity/customization pruning, profile bounds and settings cancellation. Fixed diagnostic events use static component/event identifiers only and do not log network identifiers or raw exception payloads.

Implementation changes are on work/data-architecture; main remains unchanged. Phase 4 is not closed until the final branch head passes Android Lint, unit tests, Debug APK and Release APK in CI. Device-specific validation, repository/integration tests and Compose UI tests remain explicitly tracked in Phase 6.


## Phase 4 completion — 2026-11-15

Phase 4 is closed on `work/data-architecture`. The final head `4cc6d383df5ceda30f171072c93820ec4d7ce55d` passed full GitHub Actions run [#43714028759](https://github.com/skrskr7799-design/LanGhost/actions/runs/43714028759): Android Lint, all unit tests, Debug APK, Release APK and artifact upload. Every workflow step completed successfully. The only failure in the preceding run was a JUnit 4 test-method return-type issue in `DataRetentionTest`; the test now explicitly returns Unit and the full workflow is green. The branch is 211 commits ahead of main and 0 behind; main remains unchanged. Phase 6 device/integration/Compose validation remains open and is not implied by CI success.

## Phase 5 kickoff — 2026-11-15

The full visual redesign is now active. Initial source audit confirms that the current UI is functionally broad but visually repetitive: the dashboard is dominated by a long action list, screens independently recreate similar Material scaffolds and cards, phone navigation lacks a persistent primary navigation surface, tablet navigation is a fixed split, and the theme has only a small dark token set. The redesign will establish a reusable visual system and a coherent application shell before replacing individual screens. Existing network behavior, permission disclosures, evidence freshness, scan coverage, privacy boundaries and accessibility semantics must be preserved. See `docs/REDESIGN_PLAN.md` for the visual direction and implementation sequence.


## Pre-redesign functional gate — 2026-11-16

The working branch has completed the first implementation pass across the network engine, security evidence, adaptive navigation shell, overview, devices, device details, topology, nearby Wi-Fi, channel analysis, security, public-IP lookup, network profiles, Wake-on-LAN profiles, utilities and settings. This is a functional/source-review checkpoint, not the final cohesive visual redesign.

The final CI run on the latest branch head must complete Android Lint, unit tests, Debug APK and Release APK successfully before the automated gate is closed. Source review additionally corrected notification-permission coupling in background monitoring, bounded/cancellation-aware DNS utility lookups, and updated the README to describe current capabilities and limitations.

Still outside the automated gate: Compose UI/integration tests are not yet present; real-device testing is required for Android permission and biometric lifecycle behavior, Wi-Fi scan throttling/redaction, ARP visibility, VPN/multi-network routing, router/mesh behavior, and background scheduling. IPv6 discovery, channel-width/regulatory-aware analysis and true physical topology remain explicit limitations rather than claims of completed functionality. The full visual redesign remains a separate cohesive stage after the functional gate.


### Phase 7 implementation log — 2026-11-16

**7.1 source trace: COMPLETE.** The active-interface and subnet selection path, strict ARP parser, bounded candidate generator, six-port positive TCP probe, cancellation/network-scope guards and coverage accounting have been traced. The source-level findings are recorded above.

**7.2 first reliability improvement: IMPLEMENTED, CI PENDING.** After the bounded TCP candidate pass, the engine now revalidates the active network scope, re-reads the ARP table for the same active interface, revalidates scope again, and merges fresh in-subnet ARP observations with the initial ARP snapshot and positive TCP evidence. This allows a host that answers neighbor resolution but exposes none of the six TCP probe ports to be included when Android's kernel has learned its MAC address. Out-of-subnet entries remain excluded. No host is marked offline from a failed TCP probe.

Regression coverage added for:
- post-probe ARP refresh discovering a host with no open probed TCP ports;
- filtering refreshed ARP evidence to the active subnet;
- cancellation before the refreshed ARP read and revalidation immediately after it returns;
- deterministic concurrent probe-count assertions (the pre-existing test used an unsynchronized shared counter across IO-dispatched coroutines).

**Important limitation:** this change improves use of ARP evidence generated during the existing probe pass; it does not guarantee visibility of every LAN client. Android/kernel neighbor-cache behavior, router client isolation, sleeping devices, subnet truncation and scan timing still require physical validation. NSD/mDNS and other candidate evidence sources remain under evaluation, not yet committed.

The next implementation gate is full CI on the final Phase 7 branch head, followed by review of scan latency/coverage and an on-device retest against the known home-LAN device list.

## Phase 7 kickoff — Network Discovery Reliability (2026-11-16)

The first tablet audit identified a high-impact discovery discrepancy: only the tablet, router and server appeared in the inventory, while the user confirmed a computer, TV and two phones were also connected to the same home network. The scan of nearby Wi-Fi access points appeared to work; this finding concerns host discovery, not access-point scanning.

Phase 7 is active on `audit/phase-7-discovery-reliability`, branched from main commit `6c36723497925e69d97081f944f82ff6bd4dc9e4`. The scope and exit criteria are recorded above. No root cause is declared yet. The first implementation task is a source-level trace of candidate generation, active-interface selection, ARP neighbor collection, positive TCP evidence, endpoint merging and enrichment. The audit must specifically test the current design's dependence on positive TCP responses and determine whether additional Android-compatible discovery evidence can safely improve coverage.

### Phase 7.1 — Initial source audit findings (2026-11-16)

The first source trace confirms the following current behavior:

- `NetworkRepository.scanDevices()` reads `/proc/net/arp` once **before** the subnet TCP-probe pass. It does not deliberately prime the neighbor table first and then re-read it.
- `parseArpTable()` accepts only complete ARP entries (ATF_COM), usable unicast MAC addresses and the exact active interface. `NetworkDiscoveryEngine` then filters those entries to the current IPv4 subnet. This is conservative, but it means a client absent from the kernel neighbor table at that instant contributes no ARP evidence.
- Candidate discovery probes each usable IPv4 address through TCP ports `80, 443, 22, 445, 8080, 53`. A host is included from this path only if one of those TCP connections succeeds. Refused, filtered, timed-out and other failed probes are intentionally treated as unconfirmed, not as proof of offline status.
- Consequently, a live client that has not recently populated the ARP table and does not accept a connection on those six ports can be omitted. This is a confirmed limitation of the current implementation and a strong candidate explanation for the tablet audit; it is **not yet proof** that this was the only cause on the user's router.
- The current probe budget is bounded (up to 1 second per host, with at most 32 concurrent host probes); subnet candidate generation is capped at 1,024 and scan coverage reports checked versus total usable addresses.

**Next investigation:** evaluate a bounded neighbor-discovery/ARP-priming stage followed by a fresh neighbor-table read, plus stock-Android-compatible service discovery (for example, carefully scoped NSD/mDNS). Compare reliability, Android API behavior, permissions, multicast requirements, cancellation and battery cost before selecting an implementation. Do not add a blanket ping/port sweep or treat absence of a response as host absence.

The user also reported missing vendor/OS identification. This is tracked as Phase 8 rather than mixed into discovery reliability. Nearby Wi-Fi interactivity, discovered-device-to-Wake-on-LAN integration, ping layout stability and navigation consistency are tracked in Phases 9–11. The tablet's real-network retest is a required Phase 7 exit criterion; CI alone cannot close the phase.

**Phase status rule:** a phase is marked complete only after implementation, relevant regression tests, full CI review, documentation update and any required physical-device acceptance. Until then it remains explicitly IN PROGRESS or BLOCKED, never checked off by assumption.


### Phase 7.2 — Evidence semantics hardening (implementation checkpoint, 2026-11-16)

The discovery pipeline now carries two separate outputs instead of flattening all sources into one undifferentiated set:

- tcpResponsiveIps: hosts for which a TCP connection to one of the six discovery ports succeeded during this scan;
- arpAddresses: in-subnet MAC observations from the active interface's ARP table, including the post-probe refresh.

Each Device now retains the time of its latest observation (lastSeen) separately from the latest positive reachability confirmation (lastConfirmedOnline), plus the source of its latest observation (DiscoveryEvidence). A passive/cached ARP row can add an inventory observation and MAC address, but it does not refresh the online timer. The local interface is treated as locally present; a configured gateway without positive response is not silently promoted to confirmed-online.

The Devices inventory now labels ARP-only and unconfirmed gateway observations explicitly instead of presenting them as ordinary offline devices. Lifecycle ordering uses the confirmed-reachability timestamp, while offline retention continues to use the last observation timestamp.

Regression tests cover:
- ARP-only evidence does not create a fresh online confirmation;
- a new ARP observation does not refresh an older TCP confirmation;
- positive TCP discovery is carried separately from the merged inventory;
- local-interface presence remains confirmed;
- gateway evidence is classified independently from a passive MAC observation.

This is an implementation checkpoint only. Full CI for the current branch head and physical retest remain pending. At the time of this checkpoint, the six-port probe still counted only successful TCP connections; connection-refused/RST handling was added and regression-tested in Phase 7.3. NSD/mDNS is a complementary discovery path, not a universal LAN inventory guarantee.


### Phase 7.2 — DNS-SD / NSD complementary discovery checkpoint (2026-11-16)

A bounded Android NSD adapter is now integrated into the active-network scan. It concurrently listens for a fixed set of common DNS-SD service types (HTTP, HTTPS, SSH, SMB, Google Cast, AirPlay and IPP), resolves advertised services to IPv4 addresses, deduplicates service observations, caps outstanding resolutions at four and resolved hosts at 256, and limits each discovery listener to a bounded startup/window/grace period. Android 13+ binds discovery to the captured active Network where supported; earlier versions use the platform default network. Android 14+ uses the per-service `registerServiceInfoCallback` API; older versions use `resolveService`. Discovery and service-info listeners are stopped in `finally`; scan cancellation propagates, and the legacy multicast lock is acquired only on Android versions that still require it and released in `finally`.

Resolved addresses are filtered against the active IPv4 subnet before they can enter the device inventory. The engine runs the NSD pass alongside the post-probe ARP refresh and revalidates network scope before publishing the merged result. NSD is carried as its own `NSD_SERVICE_OBSERVATION` evidence type rather than being mislabeled as a TCP response. The device inventory displays this source as **mDNS / DNS-SD**.

Regression tests cover:
- inclusion of DNS-SD observations only when their resolved IPv4 address belongs to the active subnet, with a hard 256-host cap in the engine;
- optional DNS-SD failure isolation, cancellation propagation and cross-source deduplication;
- keeping NSD evidence distinct from TCP evidence while recording a positive service-resolution observation.

**Limitations:** DNS-SD discovers only devices that advertise one of the supported service types and respond to resolution. Phones, TVs, computers and IoT devices may advertise different services, disable mDNS, use multicast filtering/client isolation, or be asleep. A resolved service does not identify the device OS or guarantee a complete inventory. The current fixed service-type list is intentionally bounded and will be reviewed against real-device results. Service-info callbacks are tracked across all concurrent service-type listeners and cleaned up once the scan ends, so one listener cannot cancel another listener's in-flight resolution. Android target SDK remains 35; the future Android 17 local-network permission migration must be handled before targeting API 37.

**Status:** implementation checkpoint only. Lint, unit tests, APK builds and artifact upload are pending on the updated branch head. Phase 7 remains IN PROGRESS; physical home-LAN retest is still mandatory.


### Phase 7.3 — Physical retest feedback and expanded evidence plan (2026-11-16)

The first post-NSD physical retest on the user's Galaxy tablet completed successfully from an installation/launch perspective, but the device inventory was unchanged: tablet, router and server only. No additional computer, TV or phone appeared. This is a failed discovery acceptance check, not a phase completion. The result confirms that the initial ARP refresh and fixed DNS-SD service set have not materially improved coverage on this home LAN; it does not by itself distinguish between router/client isolation, endpoint sleep/firewall behavior, subnet selection, multicast visibility or an NSD listener/resolution issue.

A source review identified another concrete false-negative path: `NetworkProbeEngine.probeHost()` previously counted only a successful TCP connect as positive evidence. A connection refusal (TCP RST) is also a response from the target and must count as reachability, while timeouts and routing errors remain unknown. This is now corrected and covered by a loopback regression test.

**Next diagnostic/implementation gate:**
- Expose source-specific scan counters (candidate addresses checked, TCP-positive hosts, ARP entries, DNS-SD-resolved hosts) so a physical retest can show which evidence path is empty instead of reporting only a total coverage percentage.
- Add bounded, active-network-scoped discovery for additional LAN protocols with device relevance: SSDP/UPnP (TV/media devices), WS-Discovery (Windows hosts), and NBNS/NetBIOS (where enabled). Multicast/broadcast reception, lock lifecycle, interface binding, cancellation, packet limits and privacy must be explicitly handled and unit-tested.
- Preserve source-specific evidence. A UDP discovery response may confirm a host, but must not be mislabeled as a TCP response or OS identification.
- Review whether the subnet is fully covered by the 1,024-target cap and make truncation/source counts visible.
- Repeat the same known-device acceptance test only after the new diagnostics and discovery paths pass full CI.

**Status:** Phase 7 remains IN PROGRESS. The unchanged physical result is recorded as an unresolved acceptance failure. Do not mark the phase complete or merge PR #3 based on CI alone.


### Phase 7.4 — Multi-signal discovery and scheduler hardening (implementation checkpoint, 2026-11-16)

The follow-up code audit identified three additional reliability/performance weaknesses and proposed complementary host-discovery signals. The following changes are now implemented on `audit/phase-7-discovery-reliability`:

**TCP probing**
- A refused TCP connection (RST / `ConnectException`) remains positive reachability evidence; timeouts and routing failures remain unknown.
- The six candidate ports are attempted concurrently under a hard global discovery-socket limit of 192 (32 host workers × 6 candidate ports). The per-port connect timeout is capped at 400 ms and the host deadline remains bounded. As soon as any port returns a positive response (open or refused), sibling sockets are closed rather than waiting for their timeouts.
- The default scan timeout was raised from 150 ms to 350 ms. Hosts without positive TCP evidence receive one shorter second pass; hosts already confirmed in the first pass are not probed again.
- Regression coverage verifies RST evidence and that only unresolved hosts are retried.

**Continuous bounded scheduling**
- `HostDiscovery.mapBounded` and subnet discovery now use a bounded sliding worker scheduler rather than fixed batches. A completed task immediately frees a slot for the next candidate; the scheduler does not wait for the slowest host in a batch.
- Network-scope checks run in the parent scheduler at each sliding window, so a network change propagates cancellation directly and prevents the next window from being admitted.
- Discovery and manual/security port scans use separate bounded socket semaphores.

**ARP diagnostics**
- `/proc/net/arp` read failures are no longer silently indistinguishable from a valid empty table. A fixed, privacy-safe `AppLog` event records the failure without logging IPs, MACs, SSIDs or exception payloads.
- Scan coverage carries nullable `arpAvailable` state, and the inventory UI displays **ARP недоступен** when the platform denies or cannot provide the table.

**Additional discovery signals**
- Existing NSD/mDNS remains in place.
- A bounded SSDP/UPnP M-SEARCH pass is added. Its UDP socket is bound to the captured active Android Network before sending; only valid HTTP 200 SSDP responses with service identity headers are accepted; response source addresses are subnet-filtered and capped at 256.
- A best-effort interface-bound `InetAddress.isReachable(NetworkInterface, ...)` pass runs only for candidates without positive TCP evidence. Depending on Android/JVM implementation this may use ICMP echo or a TCP-echo fallback; it is therefore labelled as a host-reachability probe rather than guaranteed ICMP. A negative result never means offline.
- TCP, DNS-SD, SSDP and host-reachability observations remain distinct evidence types and have separate scan counters. They are not treated as OS identification.
- Optional SSDP/reachability failures are isolated from other discovery evidence; cancellation and active-network scope checks still propagate.

**Limitations and physical acceptance**
- SSDP only discovers devices that answer UPnP/SSDP M-SEARCH; it will not find every phone or computer.
- `InetAddress.isReachable` is platform-dependent and many endpoints/firewalls suppress echo traffic. It is supplemental, not a guaranteed ping implementation.
- Android's ARP table may remain inaccessible; the UI now makes that source failure visible rather than pretending the source returned zero hosts.
- Client isolation, multicast filtering, sleeping devices, subnet candidate caps and endpoint firewall policy can still prevent discovery. The next tablet test must compare source counters as well as the total inventory against the known devices (tablet, router, server, computer, TV and both phones).

**Status:** implementation checkpoint only. Full CI on the final head and the physical home-LAN retest are still required. Phase 7 remains IN PROGRESS; do not merge PR #3 or mark the phase complete yet.


**Phase 7.4 CI validation checkpoint — 2026-11-16**

The final implementation head for this checkpoint, `b746dd58f47cc3182881c8fb82c527862eaaf521`, passed GitHub Actions run [#43865999257](https://github.com/skrskr7799-design/LanGhost/actions/runs/43865999257). Android Lint, all 147 unit tests, Debug APK build, Release APK build and APK artifact upload completed successfully. The uploaded artifact is `langhost-apks` (Debug APK plus unsigned Release APK). An earlier test attempt exposed two cancellation assertions tied to the previous batch scheduler; rather than weakening cancellation behavior, the scheduler was replaced with a bounded sliding worker implementation that propagates scope-check failures from the parent coroutine. The complete test suite is green on the recorded head.

**Phase status remains IN PROGRESS.** The new Debug APK is ready for the next physical home-LAN acceptance pass. The tablet test has not yet been performed on this implementation, so the known-device list and source counters still need to be verified before Phase 7 can close. PR #3 remains open and unmerged.
