# WatchRelay

Sideload-only Android app for a Samsung Galaxy S25 Ultra (and any phone on API 26+). It receives Apple Watch / Apple Health fitness exports over local Wi‑Fi, stores them on the phone, shows them in several views, and uploads workouts to Strava.

Apple Watch cannot pair with Android. WatchRelay does not fake that. Put the iPhone and the Galaxy on the same Wi‑Fi, start **Wi‑Fi receive**, then send a Health or workout file to the URL on the Receive screen (or use Import / share-to-app).

## What it imports

GPX, TCX, FIT, Apple Health `export.xml`, Health `export.zip`, JSON (Health Auto Export / Shortcuts style), and CSV.

Each workout can be opened as **Summary**, **Route**, **Heart**, **Splits**, and **Raw**.

## Sideload the APK

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # or your JDK 17+
export ANDROID_HOME=$ANDROID_HOME                     # Android SDK

./gradlew test assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to the phone and open it (allow installs from that source).

## Strava

1. Create an API application at [strava.com/settings/api](https://www.strava.com/settings/api).
2. Set the callback domain / redirect to `watchrelay://strava/callback`.
3. Paste the client id and secret in **Settings**.
4. Connect on the Strava screen, select workouts, upload.

Credentials and tokens stay in on-device encrypted preferences. There is no WatchRelay server.

## Permissions

Wi‑Fi / nearby devices, internet (Strava + LAN listener), notifications for the receive service, and the system file picker for local storage. Original files are stored under the app’s private `files/imports/` directory.
