# Chime Reminder — Build Instructions

## Prerequisites

| Tool | Version | Download |
|------|---------|----------|
| Android Studio | Hedgehog 2023.1+ (or newer) | developer.android.com/studio |
| JDK | 17 (bundled with Android Studio) | — |
| Android SDK | API 35 | via Android Studio SDK Manager |
| Python 3 | 3.8+ (for chime generation) | python.org |

---

## Step 1 — Generate the chime sound

Run once from the project root directory:

```bash
python generate_chime.py
```

This creates `app/src/main/res/raw/chime.wav` (~105 KB, 1.2-second descending chime).

Alternatively, place any short WAV/MP3 file (≤5 s recommended) at that path.

---

## Step 2 — Open in Android Studio

1. Open Android Studio
2. **File → Open** → select `D:\PROJECTS\AndroidChimeReminder`
3. Wait for Gradle sync to complete (first run downloads ~500 MB)

If Android Studio shows "Gradle files have changed" → click **Sync Now**.

---

## Step 3 — Build the APK

### Debug APK (for testing)

```bash
# From project root — OR use Android Studio Build menu
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Install directly on a connected device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Release APK (for Play Store)

1. Create a signing key:
   **Build → Generate Signed Bundle/APK → APK → Create new keystore**

2. Add signing config to `app/build.gradle.kts`:
   ```kotlin
   signingConfigs {
       create("release") {
           keyAlias = "your_alias"
           keyPassword = "your_key_password"
           storeFile = file("your_keystore.jks")
           storePassword = "your_store_password"
       }
   }
   buildTypes {
       release {
           signingConfig = signingConfigs.getByName("release")
           // ... existing config
       }
   }
   ```

3. Build:
   ```bash
   ./gradlew assembleRelease
   ```

   Output: `app/build/outputs/apk/release/app-release.apk`

---

## Step 4 — Grant permissions (first launch)

On the device, when the app opens:

- **Android 13+**: Accept the notification permission prompt
- **Android 12–13**: Tap the yellow banner → grant "Alarms & reminders" in Settings

---

## Verification checklist

1. Add a one-time reminder 1 minute from now → chime plays at the correct time
2. Set phone to silent/vibrate → chime still plays (uses alarm audio stream)
3. Play music → music ducks briefly during chime, then resumes
4. Add two reminders at the same minute → chimes play sequentially
5. Reboot the device → reminders survive (BootReceiver reschedules them)
6. History tab shows triggered reminders with timestamps
7. Toggle disable on a reminder → no chime; re-enable → schedules immediately

---

## Project structure overview

```
app/src/main/java/com/valamas/chimereminder/
├── App.kt                          Application class (DB singleton)
├── MainActivity.kt                  Single-activity host
├── data/                           Room entities + DAOs
├── alarm/
│   ├── AlarmScheduler.kt           Schedules/cancels alarms via AlarmManager
│   ├── AlarmReceiver.kt            BroadcastReceiver → starts ChimeService
│   └── ChimeService.kt             ForegroundService — queues + plays chimes
├── receiver/
│   └── BootReceiver.kt             Restores alarms after reboot
├── ui/
│   ├── reminders/                  List + Add/Edit screens
│   └── history/                    Triggered-chime log screen
└── viewmodel/                      MVVM ViewModels
```

---

## Google Play Store — submission notes

| Requirement | How it is met |
|---|---|
| Exact alarms | `USE_EXACT_ALARM` (API 33+, auto-granted) + `SCHEDULE_EXACT_ALARM` (API 31–32) |
| Battery optimisation | NOT requested — `setAlarmClock()` bypasses Doze legitimately |
| Foreground service | `foregroundServiceType="mediaPlayback"`, silent notification |
| No ads / no tracking | No analytics, no third-party SDKs |
| Permissions justified | Only permissions strictly required for alarm + audio |

**Suggested Play Store listing excerpt:**

> Chime Reminder plays a gentle chime at scheduled times — no popups, no interaction needed.
> Perfect for medication reminders, hourly chimes, and time-boxing.
> Works reliably even in silent mode, during calls, and after reboot.
> Supports one-time, daily, and custom-day schedules with unlimited reminders.
> Free, no ads, no data collection.
