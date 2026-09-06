# WatchRelay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a sideload Android APK that receives Apple Watch fitness files over Wi-Fi, parses GPX/TCX/FIT/Health XML/ZIP/JSON/CSV, stores them locally, shows Summary/Route/HR/Splits/Raw views, and uploads to Strava.

**Architecture:** One Gradle module. Pure-Kotlin parsers and models in `com.watchrelay.core` so JVM unit tests run without an emulator. Android UI, Room, NSD, and the HTTP receive service live in `com.watchrelay.app`. Normalized `Workout` records are the only type the UI and Strava exporter consume.

**Tech Stack:** Kotlin 2.0, Jetpack Compose Material 3, Room, OkHttp, NanoHTTPD, Android NSD, EncryptedSharedPreferences, Gradle 8.11 + AGP 8.7, JUnit 4.

## Global Constraints

- Package `com.watchrelay.app`, app label WatchRelay.
- `minSdk` 26, `targetSdk` 35, `compileSdk` 35.
- Local-install APK only; no Play Store metadata.
- No native Apple Watch pairing; Wi-Fi receive + file import only.
- Permissions limited to network/Wi-Fi, nearby devices, notifications, and the data-sync foreground service. Files come through SAF / app storage.
- Strava uses user-supplied client id/secret and redirect `watchrelay://strava/callback`.
- Parsers must be JVM-testable and must not reference Android types.

## File map

- `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/*` — build
- `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml` — module + permissions
- `app/src/main/java/com/watchrelay/core/model/Models.kt` — `Sport`, `Workout`, `TrackPoint`, `Split`, `HeartSample`, `ImportResult`
- `app/src/main/java/com/watchrelay/core/importing/FormatDetector.kt`
- `app/src/main/java/com/watchrelay/core/importing/GpxParser.kt`
- `app/src/main/java/com/watchrelay/core/importing/TcxParser.kt`
- `app/src/main/java/com/watchrelay/core/importing/FitParser.kt`
- `app/src/main/java/com/watchrelay/core/importing/HealthXmlParser.kt`
- `app/src/main/java/com/watchrelay/core/importing/JsonWorkoutParser.kt`
- `app/src/main/java/com/watchrelay/core/importing/CsvWorkoutParser.kt`
- `app/src/main/java/com/watchrelay/core/importing/ZipImport.kt`
- `app/src/main/java/com/watchrelay/core/importing/ImportCoordinator.kt`
- `app/src/main/java/com/watchrelay/core/export/GpxWriter.kt`
- `app/src/main/java/com/watchrelay/core/export/StravaSport.kt`
- `app/src/main/java/com/watchrelay/app/*` — Application, Activity, theme, navigation, screens, Room, receive service, Strava client
- `app/src/test/java/com/watchrelay/core/*` — parser tests
- `app/src/test/resources/fixtures/*` — tiny real-shaped files

---

### Task 1: Gradle project + failing detector test

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`, `app/build.gradle.kts`, wrapper files
- Create: `app/src/test/java/com/watchrelay/core/FormatDetectorTest.kt`
- Create: `app/src/main/java/com/watchrelay/core/importing/FormatDetector.kt` (after red)

**Interfaces:**
- Consumes: file name + bytes
- Produces: `enum class FitnessFormat { GPX, TCX, FIT, HEALTH_XML, HEALTH_ZIP, JSON, CSV, UNKNOWN }` and `FormatDetector.detect(fileName: String, bytes: ByteArray): FitnessFormat`

- [x] Scaffold Gradle (config files; TDD exception)
- [x] Write `FormatDetectorTest` for extension + magic-byte cases
- [x] Implement `FormatDetector`
- [x] `./gradlew :app:testDebugUnitTest` for detector + remaining parsers

### Task 2: Parsers (TDD per format)

**Produces:** `ImportCoordinator.import(fileName: String, bytes: ByteArray): List<Workout>`

Each parser returns `List<Workout>` with `sourceFormat`, `sourceFileName`, points/samples/splits when present.

Fixtures: `run.gpx`, `bike.tcx`, `session.fit`, `export.xml`, `workouts.json`, `workouts.csv`, `export.zip`.

### Task 3: Room + repository + GPX writer + Strava mapping

### Task 4: Wi-Fi receive service + NSD + upload HTML

### Task 5: Compose UI (Home, Receive, Library, Workout tabs, Import, Export, Settings)

### Task 6: Verify `./gradlew test assembleDebug` and sideload APK path

---

Inline execution on this cloud agent: implement tasks in this session, commit as slices land, then assemble the APK.
