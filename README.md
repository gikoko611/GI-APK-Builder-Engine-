# G.I Notes — APK Builder Test Project

A small offline Android notes app intended as a test project for G.I APK Builder.

## Features
- Create notes with a title and body
- Delete notes
- Notes persist locally on the device
- No internet permission and no API key required
- GitHub Actions workflow builds a debug APK

## Build
Requires JDK 17 and Gradle 8.9.

```bash
gradle --no-daemon assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## GitHub Actions
Upload this project to a GitHub repository, then open **Actions**. The workflow builds the APK and uploads `GI-Notes-debug-APK` as an artifact.
