# Shinyu Aikido Android

Native Android app concept for Shinyu Aikido, based on shinyuembody.org.

## What is included

- Native Jetpack Compose interface
- Shinyu forest, sage and cream visual system
- Home screen with next training
- Weekly Amsterdam and The Hague schedule
- Directions for each dojo
- Add the next occurrence of a class to Android Calendar
- Trial class button using the current Shinyu ClassPass link
- Live in-app pages for Aikido, teaching team, events, Aiki Leadership Lab, Samurai Game and merch
- Email, phone, website and share actions
- Android adaptive app icon

## Live Home content

The website is now the source of truth for the Home screen:

- Weekly Aiki Theme: `https://www.shinyuembody.org/Aiki-theme`
- Training schedule: `https://www.shinyuembody.org/aikido-schedule`
- Latest Aikido event/update: `https://www.shinyuembody.org/aikido-events`

The app refreshes these sources when it starts and every 15 minutes while it remains open. Successful pages are cached locally, and the bundled schedule/theme in `ShinyuData.kt` are used only as an offline fallback.

## Open and run

1. Open this folder in Android Studio.
2. Let Android Studio sync the Gradle project.
3. Use a device or emulator running Android 8.0 or newer.
4. Press Run.

The project targets Android 15 / API 35 and uses Java 17.

## Build an APK

In Android Studio:

`Build > Build App Bundles or APKs > Build APKs`

For Google Play release, create a signed Android App Bundle using:

`Build > Generate Signed App Bundle or APK`

## Suggested version 2

A production follow-up can add Firebase push notifications for class changes, event registration, member login, recurring calendar subscriptions, attendance, grading records, announcements and direct payment flows.

## One-command Linux SDK setup

The project includes `install-android-sdk.sh`. It installs a project-local Android SDK with platform 35, build-tools 35.0.0 and platform-tools, writes `local.properties`, and creates a Gradle 8.9 wrapper for Android Gradle Plugin 8.7.3.

```bash
./install-android-sdk.sh
./build-apk.sh
```

The debug APK will be written to `app/build/outputs/apk/debug/app-debug.apk`.

## Version 1.1 integrated trainer

This project now includes the native OpenGL ES Aikido movement trainer under a new **Trainer** bottom-navigation tab. It uses the same Shinyu Aikido package/application id, so it is intended as an update to the existing Android app.

## v1.3.0 - Dynamic Aikido Home

The Home screen now refreshes directly from the Shinyu website. Updating `/Aiki-theme` changes the weekly theme in the app without rebuilding the APK. The live schedule also drives the "Training now" card and the native Schedule tab. A latest Aikido update card appears when the events page exposes a usable current event heading.


## v1.3.9 Live schedule

Adds the 4 September 2026 Aiki-Stretching Zoom session to Home and Schedule, plus an optional remote JSON live-feed at `https://www.shinyuembody.org/shinyu-app-live.json`. See `LIVE_SCHEDULE_UPDATE.md`.
