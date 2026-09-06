# WatchRelay Design

Sideload-only Android app that receives Apple Watch fitness exports over local Wi-Fi, stores them on the phone, shows them in several views, and uploads workouts to Strava.

## Constraint that shapes the product

Apple Watch cannot pair with Android. watchOS pairing, HealthKit, and Watch Connectivity all require an iPhone. There is no public API that lets a Galaxy S25 Ultra become an Apple Watch companion.

WatchRelay therefore **does not claim native watch pairing**. “Connect over Wi-Fi” means:

1. The phone advertises a local HTTP receive service on the current Wi-Fi LAN (NSD/mDNS + LAN URL).
2. Workout files leave the Apple Watch / iPhone via Health export, Files, Shortcuts, or a share sheet.
3. Those files are posted to the phone (browser upload on the LAN, Android file picker, or a discovered `_watchrelay._tcp` sender).
4. The app parses every common Apple Watch / Health export format, stores the result, and can push activities to Strava.

This is the only architecture that works on a stock S25 Ultra and a stock Apple Watch.

## Product

- **Name:** WatchRelay
- **Package:** `com.watchrelay.app`
- **Distribution:** local APK only (`assembleDebug` / `assembleRelease`). No Play Store listing, no backend of our own.
- **Target device:** Samsung Galaxy S25 Ultra (API 35). `minSdk` 26 so other phones can sideload too.
- **Permissions (minimum + required companions):**
  - `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`
  - `NEARBY_WIFI_DEVICES` (API 33+)
  - `POST_NOTIFICATIONS` for the receive foreground service
  - `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`
  - Storage via the system file picker / app-specific directories (no broad media-store scrape)

## Screens

Clean, single-purpose Material 3 screens. Dark athletic theme (navy + mint). Large stats, short copy.

| Screen | Purpose |
| --- | --- |
| Home | Receive status, Wi-Fi name, last import, shortcuts to library / receive / Strava |
| Receive | Start/stop LAN listener, show `http://<ip>:8765`, incoming transfers, honest pairing note |
| Library | Imported workouts: sport, when, distance, duration, source format |
| Workout | Tabs: Summary, Route, Heart rate, Splits, Raw file |
| Import | System file picker for one or many files / Health zip |
| Export | Select workouts, connect Strava, upload GPX |
| Settings | Strava client id/secret, permissions recap, clear local data, about |

## Data formats

Apple Watch and Health-adjacent apps export these. WatchRelay must import all of them and show each workout in every applicable view.

| Format | Typical source | What we extract |
| --- | --- | --- |
| GPX 1.1 | Route share, Health route, third-party apps | Track points, elevation, time, HR extensions |
| TCX | Training Center / many watch exporters | Activity, laps, track, HR, cadence |
| FIT | Garmin-style exporters used with Apple Watch apps | Session, laps, records (GPS, HR, cadence, distance) |
| Apple Health `export.xml` | Settings → Health → Export | `Workout`, nearby `Record` HR samples |
| `export.zip` | Same Health export | Unzip, then parse `export.xml` and workout routes |
| JSON | Health Auto Export / Shortcuts | Workout metadata, samples, route arrays |
| CSV | Spreadsheet dumps | One workout per row (date, type, duration, distance, HR, calories) |

Normalized model: `Workout` + `TrackPoint` + `Split` + `HeartSample` + original bytes/path for the Raw tab.

## Storage

On-device only.

- Room database for workouts, points, splits, samples, import jobs, Strava upload state.
- Original files in app-specific storage (`files/imports/`).
- Strava tokens in `EncryptedSharedPreferences`.
- No cloud sync besides the user’s explicit Strava upload.

## Wi-Fi receive

Foreground service `ReceiveService`:

- Binds `0.0.0.0:8765`
- `GET /` — tiny upload page (phone or iPhone Safari on the same LAN)
- `POST /import` — multipart file; saved and parsed immediately
- `GET /api/status` — listening flag + import count
- NSD type `_watchrelay._tcp`

The receive screen also lists NSD peers in case a future companion sender appears. Discovery is best-effort; file receipt is the supported path.

## Strava

User supplies their own Strava API client id and secret (Settings). OAuth redirect: `watchrelay://strava/callback`.

Upload: `POST https://www.strava.com/api/v3/uploads` with a GPX generated from the stored workout (`activity_type` mapped from sport). Tokens refresh with the standard OAuth refresh grant.

If credentials are missing, Export explains how to create a Strava API application and refuses to pretend the upload succeeded.

## Testing and build

- JVM unit tests for format detection and every parser (pure Kotlin + SAX, no emulator).
- `./gradlew test assembleDebug` produces a sideload APK at `app/build/outputs/apk/debug/app-debug.apk`.
- Sample fixtures live under `app/src/test/resources/fixtures/`.

## Out of scope

- Native Apple Watch pairing, Watch Connectivity, or HealthKit on Android.
- Play Store release, crash analytics, accounts, or a WatchRelay server.
- Live heart-rate streaming from the watch.
- A compiled watchOS companion (cannot be built on this Linux agent).
