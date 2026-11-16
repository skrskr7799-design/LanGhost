# LanGhost

**Android local-network discovery and diagnostics**

LanGhost is a personal network-inspection tool for Android. It helps you discover devices on a local IPv4 network, inspect available Wi-Fi networks, review bounded connectivity evidence and run a small set of network diagnostics.

> LanGhost is intended for networks and devices you own or are authorized to assess. Network observations are best-effort: Android permissions, router isolation, VPN routing and Wi-Fi scan throttling can limit visibility.

## Features

### Network overview and discovery
- Bounded IPv4 subnet discovery with explicit checked-address coverage and truncation warnings.
- Combines usable ARP neighbor entries with positive TCP reachability evidence; a failed probe is not treated as proof that a device is offline.
- Device inventory with online/offline observations, vendor/hostname hints, favorites, watch lists, notes and per-network identity handling.
- Device details with available endpoint information and user-initiated diagnostics.
- Network-scoped scan history and change tracking.

### Wi-Fi and network intelligence
- Nearby access-point scan with 2.4, 5 and 6 GHz band filtering, search and signal-strength sorting.
- Channel congestion estimates based on access points actually observed during a fresh scan.
- Security review that requires fresh, network-scoped evidence and distinguishes open, closed, filtered and unknown TCP-port outcomes.
- Logical topology view of discovered devices, with an explicit warning that inferred grouping is not a confirmed physical or wireless link.

### Tools and monitoring
- TCP reachability checks on ports 80 and 443, plus DNS lookup.
- Explicit, user-initiated public-IP lookup.
- Wake-on-LAN profiles.
- Named network profiles.
- Optional periodic device monitoring through WorkManager, with optional new-device notifications.
- Home-screen scan widget.
- TXT/CSV device exports and shareable network summaries.
- Optional biometric/device-credential app lock.\n- Optional presentation masking for IP, MAC and BSSID identifiers across the interface, clipboard and shared exports/reports.

## Important limitations

- Host discovery currently targets IPv4. IPv6 host discovery is not implemented.
- Discovery is bounded for time and resource use. Large subnets may be truncated; the app reports coverage rather than implying a complete scan.
- Android may redact network identifiers or throttle nearby Wi-Fi scans. Results vary by Android version, permissions, device hardware and router configuration.
- A successful TCP connection confirms that a port accepted a connection at scan time; it does not prove which application protocol is running. Port-based service names are hints, not protocol fingerprints.
- Channel recommendations are estimates from observed access points. They do not validate router support, regulatory domain, channel width or DFS availability.
- The topology is a logical grouping of discovered devices, not a map of confirmed physical connections.
- Security scoring is withheld when required evidence is missing, stale, incomplete or from another network.

## Privacy and external requests

- Local discovery and diagnostics operate against the selected/current network and user-supplied targets; the app does not need to upload a local-network inventory to perform these scans.
- Public-IP lookup is not automatic. The user must start it after viewing the in-app disclosure. The primary HTTPS service is `ipapi.co`, which receives the public source IP and returns provider/approximate-location data. If that lookup fails, `api.ipify.org` is used as an IP-only fallback.
- User settings, named profiles, device customizations and bounded ping history are stored locally using Android DataStore. Transient scan evidence is not restored as current after process death.
- Exports and reports are generated only after a user action and are shared through Android's share sheet.

## Build requirements

- Android SDK Platform 35
- JDK 17
- Gradle 8.9 (provided by the checked-in Gradle Wrapper)

## Build

Clone the repository and open it in Android Studio, or build from the command line:

```bash
git clone https://github.com/skrskr7799-design/LanGhost.git
cd LanGhost
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat :app:assembleDebug`.

The first Wrapper run downloads the pinned Gradle distribution automatically. No globally installed Gradle is required.

Run the local verification tasks:

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleRelease
```

GitHub Actions runs Android Lint, unit tests, and Debug/Release APK builds on pushes to the main, work and audit branches, and on pull requests. APKs are retained as workflow artifacts. A GitHub Release is published only when a version tag matching `v*` is pushed.

## Project status

The `main` branch contains the completed network-engine, security-evidence, data-architecture and broad UI redesign work merged through PR #1. The current Phase 5 final-polish pass tightens accessibility semantics and recoverable operation feedback while keeping the interface evidence-first and privacy-aware.

Automated CI verifies Android Lint, unit tests and Debug/Release APK builds. It does not replace hands-on validation on real Android devices and networks. TalkBack, system font scaling, touch targets, visual review across phone/tablet/landscape, Android permission behavior, Wi-Fi scan throttling, ARP visibility, VPN/multi-network behavior and biometric lifecycle checks remain explicit device-validation work in `docs/AUDIT_PLAN.md`.

## App stack

- Kotlin and Jetpack Compose / Material 3
- Coroutines and Flow
- Android DataStore and WorkManager
- Minimum SDK 26 / Target SDK 35

## License

Private project.
