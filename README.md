# Family Lock

An Android app that locks a child's phone during times a parent chooses, like bedtime or homework
time. Only the parent can unlock it or change the rules, using a PIN on the child's phone.

## What it does

- **Schedules.** For example "Bedtime, every day 21:00–07:00" or "Homework, weekdays 16:00–18:00".
  Blocks can run past midnight, and you can add as many as you like.
- **Full-screen lock.** When a schedule starts, a lock screen covers the whole phone. Pressing
  Home or Recents doesn't get past it.
- **Parent PIN.** On the lock screen a parent can give extra time (15 min, 30 min or 1 hour),
  end the current lock, or open the parent settings.
- **Lock now.** Lock the phone right away for 30 minutes, 1 hour, 2 hours, or until you unlock it.
- **Calls always work.** The phone app, the emergency dialer and incoming calls are never blocked.
- **Allowed apps.** Pick other apps that still work during a lock, for example Messages or a school app.
  They are shown as buttons on the lock screen, so the child can open them from there.
- **Warning before a lock.** The child gets a notification 5, 10 or 15 minutes before a lock starts.
- **English and Turkish.** The app follows the phone's language. A parent can also pick
  English or Türkçe in the app's *Settings › Language*. On Android 13 and newer this also
  appears in the phone's own settings under *Apps › Family Lock › Language*.
- **Recovery code.** A 12-digit code is shown once during setup. If you forget your PIN, type the
  code instead.
- **Protection against getting around it:**
  - Uninstall protection (device admin): the app can't be uninstalled or force-stopped.
  - Trying to turn off the protection in Settings locks the phone until the parent PIN is entered.
  - If automatic date & time is turned off, the phone locks, so the clock can't be turned back
    to skip a lock.
  - Wrong PINs: after 5 wrong tries, PIN entry is blocked for 30 seconds, and the wait doubles
    with each further wrong try.
  - Optional "device owner" mode also blocks safe mode, factory reset, extra users and USB
    debugging (see below).

Everything stays on the phone. The app has no internet permission.

## Install

### Option A: download the APK from GitHub

1. Open the **Actions** tab of this repository and pick the latest green **Android build** run.
2. Download the `family-lock-debug-apk` artifact and unzip it.
3. Copy `app-debug.apk` to the child's phone and open it. Android asks you to allow installing
   apps from that source. Allow it, then install.

### Option B: build it yourself

Open the project in Android Studio and press Run, or from a terminal:

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Option C: signed release APK (for sharing)

Use this one when you give the app to other people. A debug APK can be inspected and changed
over USB debugging, and only a release APK signed with the same key can be installed as an update.

1. Create a keystore once and keep a backup of it. If you lose it, you can't ship updates.
   ```bash
   keytool -genkeypair -keystore ~/keystores/family-lock-release.jks -storetype PKCS12 \
     -alias family-lock -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Family Lock"
   ```
2. Add it to `~/.gradle/gradle.properties` (not to this repository):
   ```properties
   familyLock.storeFile=/home/<you>/keystores/family-lock-release.jks
   familyLock.storePassword=<password>
   familyLock.keyAlias=family-lock
   familyLock.keyPassword=<password>
   ```
3. Build:
   ```bash
   ./gradlew assembleRelease
   # APK: app/build/outputs/apk/release/app-release.apk
   ```

Raise `versionCode` in `app/build.gradle.kts` for every new version you share, so it installs
as an update.

## Setup on the child's phone

1. Open **Family Lock** and tap **Get started**.
2. Create a parent PIN (4–8 digits) and enter it again.
3. **Write down the recovery code** and keep it away from your child. Then tap **Finish**.
4. On the dashboard, go through the **Protection** list:
   - **Lock service** (required): tap *Turn on*, then open *Family Lock* in the Accessibility
     settings and switch it on.
     On Android 13 and newer, sideloaded apps first need permission for this. If the switch is
     greyed out, go back, tap *App info* › **⋮** (top right) › **Allow restricted settings**,
     then try again.
   - **Uninstall protection**: tap *Turn on* and confirm.
   - **Warnings before a lock**: tap *Allow*.
   - **Automatic date & time**: make sure automatic date, time and time zone are all on.
5. Add your schedules, for example *Bedtime*, from 21:00 until 07:00, every day.
6. Tap **Done**. The next time the app is opened, it asks for the PIN.

## Optional: device owner mode (needs a computer)

A normal app can't stop a determined child from starting the phone in safe mode or doing a factory
reset. As **device owner**, the app can block those as well.

> Android only lets you set a device owner when **no accounts** are on the phone. Do this right
> after a factory reset, before adding a Google account, or remove the accounts first and add
> them back afterwards.

1. On the phone, open *Settings › About phone* and tap *Build number* 7 times to unlock
   Developer options, then turn on *USB debugging*.
2. Install [adb](https://developer.android.com/tools/releases/platform-tools) on the computer
   and connect the phone with a USB cable.
3. Run:
   ```bash
   adb shell dpm set-device-owner io.github.ercanakalar.restrictions/.guard.AdminReceiver
   ```
4. Open the app. The dashboard shows *Device owner: On*. Developer options and USB debugging are
   now blocked as well.

**Keep your recovery code safe.** In this mode a factory reset is blocked too, so the PIN or the
recovery code is the only way to remove the app.

## Removing the app

In the app, open *Settings › Remove protection*, confirm, then tap *Uninstall*. This also ends
device owner mode.

## Limits you should know about

- Without device owner mode, a child who knows how can start the phone in **safe mode**, where
  no downloaded app runs, or **factory reset** the phone. A factory reset wipes the phone, so
  you would notice.
- Without device owner mode, a second user or guest account on the phone isn't covered.
  Device owner mode blocks adding and switching users.
- Switching off the lock service in Accessibility settings is detected from the page title.
  This works on Pixel, Samsung and most other phones, but some manufacturers' settings may
  look different.
- Some manufacturers (for example Xiaomi or Huawei) have extra battery or "autostart"
  settings. If the lock stops working after a while, allow Family Lock to run in the
  background there.

## How it works

| Part | File |
| --- | --- |
| Schedule maths (overnight blocks, back-to-back blocks, next lock) | `core/ScheduleEngine.kt` |
| Which lock applies right now, and why | `core/LockPolicy.kt` |
| PIN hashing (PBKDF2), recovery code, wrong-PIN waiting time | `core/Secrets.kt` |
| Settings and lock state (SharedPreferences) | `data/AppPrefs.kt` |
| Background service: watches the time and the app in front, shows or hides the lock | `guard/GuardService.kt` |
| Full-screen lock window | `guard/LockOverlay.kt`, `ui/LockScreen.kt` |
| Uninstall protection and device owner rules | `guard/Protection.kt`, `guard/AdminReceiver.kt` |
| Parent screens | `ui/` |

The lock is an *accessibility overlay*: a window type that only accessibility services can
create. It sits above every app, the status bar and the navigation bar, and stays in place
when Home is pressed. The same service also starts again by itself after a reboot.

The `core` package is plain Kotlin, and its unit tests run on the JVM:

```bash
./gradlew testDebugUnitTest
```

## Ideas for next versions

- Daily screen-time allowance (for example 2 hours a day) on top of schedules.
- A parent app on the parent's own phone for remote lock, unlock and extra time.
- Usage report: which apps were used and for how long.
