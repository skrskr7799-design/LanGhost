# LanGhost

**Local Network Reconnaissance Tool for Android**

Aggressive device discovery, port scanning, network intelligence and monitoring — with a premium tactical dark design.

## Features (v0.1)

- Detailed current Wi-Fi network information
- Device discovery via ARP table + ICMP ping sweep
- Vendor detection (OUI)
- TCP Port scanner with common service detection
- Device nicknames, notes, favorites and watch list (persisted)
- Permission handling
- Adaptive UI for phones and tablets (split-view)
- Dark tactical premium design

## Requirements

- Android 8.0+ (API 26)
- Location permission (required to read SSID and for scanning on modern Android)

## Build & Run

### Option 1: Android Studio (recommended)

1. Clone the repository:
   ```bash
   git clone https://github.com/skrskr7799-design/LanGhost.git
   ```
2. Open the project in **Android Studio** (Hedgehog or newer recommended)
3. Let Gradle sync finish
4. Connect a real device (emulator has very limited network scanning capabilities)
5. Click **Run**

### Option 2: Build APK via GitHub Actions

1. Go to the **Actions** tab of this repository
2. Select **Build Debug APK** workflow
3. Click **Run workflow**
4. After it finishes, download the APK from the artifacts section

### Option 3: Local command line

```bash
# Generate wrapper if needed
gradle wrapper --gradle-version 8.9

# Build
./gradlew assembleDebug

# APK will be at:
# app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

The app requests:
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — required to read Wi-Fi SSID and perform scans
- `NEARBY_WIFI_DEVICES` (Android 13+) — for Wi-Fi related features
- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- Coroutines + Flow
- DataStore (preferences)
- Accompanist Permissions
- Minimum SDK 26 / Target SDK 35

## Project Structure

```
app/src/main/java/com/langhost/
├── data/
│   ├── model/          # NetworkInfo, Device
│   ├── NetworkRepository.kt
│   └── DevicePreferences.kt
├── ui/
│   ├── screens/
│   │   ├── dashboard/
│   │   ├── devices/
│   │   └── device/
│   ├── navigation/
│   └── theme/
└── MainActivity.kt
```

## Roadmap

- [ ] Background monitoring (Watch Mode)
- [ ] Device history / timeline
- [ ] Expanded OUI database
- [ ] Better OS fingerprinting
- [ ] Export reports
- [ ] Widgets
- [ ] Release signing + Play Store ready build

## License

Private project.
