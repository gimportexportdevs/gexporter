# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **gexporter**, an Android companion app that serves GPX/FIT files to Garmin devices via HTTP. It works with the **gimporter** Garmin ConnectIQ app in the parent directory's gimporter/ folder.

## Build Commands

The Nix flake provides the full toolchain (Android SDK 37, JDK 17, Gradle) with
`ANDROID_HOME`/`JAVA_HOME` set, so no local Android SDK install is needed. Prefix
any Gradle command with `nix develop -c`, or enter the shell once with `nix develop`:

```bash
nix develop -c ./gradlew build              # Build the app
nix develop -c ./gradlew test               # Run all unit tests
nix develop -c ./gradlew assembleDebug      # Build debug APK
nix develop -c ./gradlew assembleRelease    # Build release APK

# One-shot release build via the flake's build-app package
nix run

# Run a single test class or method. NOTE: use the testDebugUnitTest task,
# not the aggregate `test` task, which rejects --tests.
nix develop -c ./gradlew testDebugUnitTest --tests "org.surfsite.gexporter.TestPlay"
nix develop -c ./gradlew testDebugUnitTest --tests "org.surfsite.gexporter.TestPlay.test10"
```

Without Nix, a standard Android SDK 37 / JDK 17 install and `./gradlew ...`
work the same; point `local.properties`'s `sdk.dir` at your SDK.

## Local Development Server

Run `TestRunServer.main()` to start a standalone HTTP server on localhost:22222 that serves files from `~/Downloads/`. Use this with the ConnectIQ simulator for development.

## Testing Against the ConnectIQ Simulator

A debug build can talk to the ConnectIQ simulator instead of a watch, with the real phone serving the files:

```bash
adb forward tcp:7381 tcp:7381     # simulator <-> Connect IQ SDK (TETHERED mode)
adb forward tcp:22222 tcp:22222   # simulator's web requests -> gexporter's server
adb shell am start -n org.surfsite.gexporter/.MainActivity --ez simulator true
```

Then in the simulator: Settings > Connection Type > BLE > Connected, Settings > Use Device HTTPS Requirements off, adb Connection > Start, and start gimporter with `monkeydo`. Reinstalling the APK drops the connection (the simulator shows "ADB Disconnected"); start it again. In this mode the SDK delivers the simulator's messages with an empty app id and writes to its socket on the calling thread, which `MainActivity.registerMessageListener` and `WatchLink.post` account for.

## Architecture

### HTTP Server (WebServer.java)

NanoHTTPD-based server on port 22222 with endpoints:
- `GET /dir.json` - JSON list of available tracks with query params:
  - `?type=GPX` - only return GPX files
  - `?short=1` - return relative URLs instead of full URLs
  - `?longname=1` - preserve full filename (otherwise truncated to 15 chars)
- `GET /{filename}` - Download file; GPX files are converted to FIT on-the-fly unless `?type=GPX`

### GPX to FIT Conversion (Gpx2Fit.java)

Parses GPX 1.0/1.1 files and converts to Garmin FIT course format:
- Extracts `<trk>`, `<rte>`, and `<wpt>` elements
- Applies grade-adjusted pace using Minetti walking energy cost model
- Writes FIT messages: FileId, Course, Lap, Event, Record, CoursePoint

### Conversion Options (Gpx2FitOptions.java)

- `speed` - Default pace in m/s
- `forceSpeed` - Override timestamps with calculated speed
- `use3dDistance` - Include elevation in distance calculations
- `walkingGrade` - Apply grade-adjusted pace
- `injectCoursePoints` - Add course points along the track
- `maxPoints` - Limit number of route points (for device compatibility)
- `minRoutePointDistance` / `minCoursePointDistance` - Point density control

### ConnectIQ Integration (MainActivity.java)

Communicates with Garmin devices via ConnectIQ SDK:
- App ID: `9B0A09CF-C89E-4F7C-A5E4-AB21400EE424`
- Widget ID: `B5FD4C5F-E0F8-48E8-8A03-E37E86971CEB`
- Responds to `GET_PORT` messages to tell the device which port to connect to
- With several connected Garmin devices, the watch card shows the best result of all of them (`showDeviceState`), so a watch without gimporter does not hide the Edge that has it

### Talking to the watch (WatchLink.java, WatchContent.java, SharedLocation.java)

- `WatchLink` sends messages to gimporter (app and widget, per device): push a tapped track or a shared place, ask for and delete gimporter's content on the watch. It remembers which gimporter answered; while gimporter reports that it is busy, a new push is held (up to 60 s) and sent once it is ready.
- gimporter reports its state ("importing", "imported", "started", "ready"); `MainActivity` shows it in the status line.
- `SharedLocation` turns shared text into a place: `geo:` URIs, Google Maps links (short links are resolved; place links without coordinates are geocoded via Nominatim, never guessed from the page), Organic Maps links (ge0) and plain coordinates.

### Compatibility with gimporter

All combinations of old and new versions must work. An old gimporter does not answer the new messages: gexporter then tells the user to pick the track from gimporter's list, and lists places only to a gimporter that asks for them (`locations=1`). A new gimporter with an old gexporter uses the track list as before.

## Testing

- **TestPlay.java**: GPX parsing and FIT conversion tests using sample files in `src/test/resources/`
- **TestCourseFit.java**: Decodes the written FIT to check what devices rely on (course point distances, timestamps not before the FIT epoch)
- **TestConversionGuards.java**, **TestSharedLocation.java**, **TestWatchContent.java**, **TestServedFiles.java**, **TestDirListing.java**, **TestFileTypes.java**: time parsing and input checks, place parsing, watch messages, served files
- **TestRunServer.java**: Standalone development server (not a test, despite the name)

## Key Dependencies

- **NanoHTTPD 2.3.1**: HTTP server
- **Garmin FIT SDK 21.205.0**: FIT file format
- **Garmin ConnectIQ Companion SDK 2.4.0**: Device communication
- **geodesy 1.1.3**: GPS distance calculations

## Environment

- Java 11 source/target compatibility (built with JDK 17)
- Android SDK 37 (min SDK 23)
- Nix flake (`flake.nix`) provides a reproducible environment: `nix develop`
  for a dev shell (Android SDK, JDK 17, Gradle, plus `adb`/`emulator`/`ktlint`),
  or `nix run` to build a release APK in one shot. First run downloads the
  Android SDK, so it is slow; subsequent runs are cached. Gradle itself needs
  network access on the first build to fetch the Android Gradle plugin
  (offline mode fails until the plugin is cached).

## Releasing

1. release-please opens a "chore(master): release x.y.z" PR from the conventional commits; merging it tags the release, creates the GitHub release and bumps the version (`versionCode` = MAJOR*10000 + MINOR*100 + PATCH).
2. The maintainer builds the signed App Bundle in Android Studio (Build > Generate Signed App Bundle, keystore `~/android-release.jks`); it lands in `app/release/app-release.aab`.
3. Play Console > Production > Create new release: upload the bundle, release notes as `<en-US>…</en-US><de-DE>…</de-DE>`, then Next > Save > Publishing overview > Send for review. The missing-deobfuscation-file warning is expected.
4. Release notes: only what users of the last published Play version notice, in user language, not the commit list.
5. The store listing (short and full description in 29 languages, screenshots, feature graphic) is maintained in the Play Console, not in this repo.

A debug build cannot be installed over the Play version (different signature); installing it means uninstalling the Play version first, which resets the chosen folder and settings.

## Where users report problems

Look there now and then and before a release; answer in the reporter's language, and show the text to the maintainer before posting (it is public):
- GitHub issues: https://github.com/gimportexportdevs/gexporter/issues (and https://github.com/gimportexportdevs/gimporter/issues)
- Garmin forum support thread: https://forums.garmin.com/developer/connect-iq/f/showcase/5150/gimporter-gexporter---import-fit-gpx-courses-from-your-android-phone
- Google Play reviews: Play Console > Ratings and reviews > Reviews
