# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is **gexporter**, an Android companion app that serves GPX/FIT files to Garmin devices via HTTP. It works together with the **gimporter** Garmin ConnectIQ app located in the parent directory's gimporter/ folder.

For complete project documentation covering both apps, see the parent directory's CLAUDE.md file.

## Build Commands

```bash
# Build the Android app
gradle build

# Run unit tests
gradle test

# Clean build artifacts
gradle clean

# Build debug APK
gradle assembleDebug

# Build release APK
gradle assembleRelease
```

## Architecture

### Core Components

- **WebServer.java**: NanoHTTPD-based HTTP server (port 22222) with endpoints:
  - `/dir.json` - Returns JSON list of available tracks
  - `/{filename}` - Downloads specific GPX/FIT file
  - Handles on-the-fly GPX to FIT conversion

- **MainActivity.java**: Android UI that:
  - Receives files via Android intents (ACTION_SEND, ACTION_VIEW)
  - Manages server lifecycle (start/stop)
  - Displays server status and file list
  - Handles file selection from various sources

- **Gpx2Fit.java**: Converts GPX to FIT format:
  - Configurable via Gpx2FitOptions
  - Supports waypoints, tracks, and routes
  - Handles elevation data and GPS coordinates

- **WayPoint.java**: Data structure for GPS waypoints
- **Gpx2FitOptions.java**: Configuration for GPX conversion (speed, power, etc.)

### File Handling

The app receives GPX/FIT files through Android's sharing mechanism and stores them in the configured directory for serving to Garmin devices.

## Development Workflow

1. **Local Development**:
   - Use Android Studio or command-line Gradle
   - Run `TestRunServer.main()` to start standalone server on localhost:22222
   - Connect Garmin ConnectIQ simulator to localhost for testing

2. **Testing with Real Devices**:
   - Install app on Android device
   - Share GPX/FIT files to the app
   - Start server from the app
   - Connect Garmin device to same network
   - Default server URL: `http://[phone-ip]:22222/dir`

## Testing

- **TestPlay.java**: Unit tests for GPX parsing and conversion
- **TestRunServer.java**: Web server tests and standalone server for development
- Test resources in `src/test/resources/`: Various sample GPX files

## Key Dependencies

- **NanoHTTPD 2.3.1**: Lightweight HTTP server
- **Garmin FIT SDK 21.120.0**: Official FIT file format library
- **Garmin ConnectIQ Companion SDK 2.0.3**: Android integration
- **geodesy 1.1.3**: GPS calculations and coordinate transformations
- **AndroidX libraries**: Modern Android development
- **SLF4J + logback-android**: Logging framework

## Environment Setup

The project now includes Nix flake support (`flake.nix`) for reproducible development environments with Java 23, Gradle, and Maven.