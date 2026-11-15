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
- The latest observed GitHub Actions run at review time was run #33 (43682690627), which completed with failure. Its job log reports Kotlin compilation errors; the full compiler output must be captured and mapped to the current source before fixes are selected.
- The repository contains gradle/wrapper/gradle-wrapper.properties, but the recursive tree reviewed did not contain gradlew, gradlew.bat, or gradle/wrapper/gradle-wrapper.jar.
- The README still describes several features already present in the source tree as roadmap items. Documentation needs reconciliation.
- Release build/signing and test coverage require a dedicated review; do not infer readiness from the existence of a release build type.

## Audit register

Use one entry per finding. Do not mark a suspected issue as confirmed without source evidence, a reproducible test, or a reliable platform constraint.

| ID | Area | Finding | Severity | Evidence / reproduction | Status |
|---|---|---|---|---|---|
| BUILD-001 | Build | Latest observed CI run fails during Kotlin compilation | Blocker | Actions run #33; capture complete diagnostics and verify against current HEAD | Open |
| BUILD-002 | Build | Gradle Wrapper scripts/JAR absent from reviewed repository tree | High | Recursive tree at baseline commit | Open |
| DOC-001 | Docs | README feature list and roadmap are stale relative to source tree | Low | Compare README with current modules/screens | Open |

## Definition of done

A phase is complete only when findings are recorded and resolved or explicitly accepted with rationale; relevant tests/checks pass; CI result is reviewed; documentation is updated; and no unrelated regression is knowingly left behind.

The roadmap is a living document. Reorder work when new evidence justifies it, while keeping the buildable baseline and user data safe.
