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

## Testing

- **TestPlay.java**: GPX parsing and FIT conversion tests using sample files in `src/test/resources/`
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