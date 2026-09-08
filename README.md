# BillTracker

A simple, fully local Android app for tracking monthly bills. No account, no network access, no cloud sync — your data stays on your device.

## Features

- **Custom biller list** — add your own bills with optional amount and due day
- **Mark as paid** — tap a checkbox to mark a bill paid; paid bills strike through and sort to the bottom
- **Automatic monthly reset** — bills reset on a configurable day of the month
- **Summary bar** — quick view of income, total due, and remaining balance
- **Swipe gestures** — swipe left to delete (with undo), swipe right to edit
- **Drag-to-reorder** — long-press and drag to reorder bills manually
- **Auto-sort by due date** — optional sort mode, toggled in Settings
- **Due-date reminders** — local notification the day before a bill is due
- **Dark theme** — black background, white text, green accent
- **Custom app icon**

## Tech stack

- Kotlin + Jetpack Compose
- Room (local SQLite) for persistence
- SharedPreferences for settings (reset day, sort mode, income)
- AlarmManager + NotificationCompat for due-date reminders
- No network permissions, no analytics, no third-party services

## Requirements

- Android Studio (Koala or newer recommended)
- Min SDK 24 (Android 7.0), target SDK 34
- JDK 17 (bundled with recent Android Studio)

## Building

### Option 1: Android Studio
1. Clone the repo and open it in Android Studio.
2. Let Gradle sync.
3. Run on an emulator or device (`Run ▶`), or build a debug APK via **Build → Build Bundle(s)/APK(s) → Build APK(s)**.

### Option 2: Command line
```bash
./gradlew assembleDebug
```
The APK will be output to `app/build/outputs/apk/debug/`.

### Option 3: GitHub Actions
Every push triggers `.github/workflows/build-apk.yml`, which builds a debug APK and uploads it as a workflow artifact — no local Android Studio setup required.

## Permissions

| Permission | Why it's needed |
|---|---|
| `POST_NOTIFICATIONS` | Shows a reminder notification the day before a bill is due |
| `RECEIVE_BOOT_COMPLETED` | Re-schedules reminders after the device reboots |

No internet permission is requested or used.

## Project structure

```
app/src/main/java/com/example/billtracker/
├── MainActivity.kt
├── data/            # Room entities, DAO, BillerRepository
└── ui/              # Compose screens, notification scheduler/receiver
```

## Notes

- All data lives in a local Room database on-device; uninstalling the app deletes it.
- This project targets personal use and is not published to the Play Store.
