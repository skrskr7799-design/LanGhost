# LanGhost

**Local Network Reconnaissance Tool for Android**

Aggressive device discovery, port scanning, network intelligence and monitoring — with a premium tactical dark design.

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

To run the local verification tasks:

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest
```

The GitHub Actions Android CI workflow runs lint, unit tests, and Debug/Release APK builds on pushes to the main, work, and audit branches, and on pull requests. APKs are retained as workflow artifacts. A GitHub Release is published only when a version tag matching `v*` is pushed.

## App overview

- Kotlin
- Jetpack Compose + Material 3
- Coroutines + Flow
- DataStore (preferences)
- Minimum SDK 26 / Target SDK 35
- Android 15 / API 35 compile target

## License

Private project.
