# Devices

The phones, tablet, watch and emulators Twangle is checked on, how each is set up, and the owner's rules for them. CLAUDE.md keeps only the short version (*Dev machine environment*); read this file before any step that uses a device: an install, a UI test run, a screenshot, a measurement.

Android SDK: `%LOCALAPPDATA%\Android\Sdk`. `adb` is at `<sdk>/platform-tools/adb.exe` and is not on PATH.

## Test phones
The owner takes turns between the two for the overnight run. UI tests give the same answer on either (`PhoneScreen` in `:core:testing`).

- **Xiaomi 12X**, the main development and test phone (adb serial `f813c98c`, Android 13 / MIUI 14, ≈ 393 × 873 dp, gesture navigation, up to 120 Hz), since October 2026. It is the owner's old phone, so it is usually there.
- **Samsung Galaxy S26**, the owner's daily phone (adb serial `R3GL502L08P`, Android 17 / API 37, 360 × 780 dp, three navigation buttons, always edge to edge). It is plugged in only on some days and every other night, so don't count on it. Measurements in the architecture docs and the archive from before October 2026 come from the S26 (then on Android 16), and say so.

### Xiaomi quirks
- MIUI needs *Developer options → Install via USB* for `:app:installDebug`, and *USB debugging (Security settings)* for the UI tests and `adb shell input`. Without them installs fail with `INSTALL_GRANT_RUNTIME_PERMISSIONS` or `INSTALL_FAILED_USER_RESTRICTED`. *MIUI optimization* is off.
- MIUI also blocks tests from opening an activity unless Xiaomi's background-start app-op is set, which every install resets; `scripts/ui-tests.sh` keeps setting it during a run (more in the ui-test skill).

## Tablet
**Lenovo Tab M11**, the owner's tablet, for the tablet checks (adb serial `HA244V49`, model TB330FU, Android 15 / API 35 with Lenovo's ZUI 17, 800 × 1280 dp at 240 dpi, 90 Hz, three navigation buttons in a taskbar along the bottom). The owner keeps its text size at 130 % and its screen on its side: leave both. `:app:installDebug` and the UI tests need no app-op like the Xiaomi's, but Play Protect may ask about an install (*Every device* below).

## Watch
**Samsung Galaxy Watch 5**, the owner's watch, for real-watch checks of `:wear` (model SM-R900, Wear OS 5 / API 34, 396 px round at 320 dpi ≈ 198 dp, smaller than the emulator's 227 dp; set up 2026-10-30).

- It reaches the PC only over Wi-Fi (Wear OS 3+ has no Bluetooth debugging): *Developer options → ADB debugging* and *Wireless debugging* on, Wi-Fi set to *On*, not *Auto*.
- This PC is paired with it, and adb finds it by itself under the lasting name `adb-RFAW21MWV5T-3XuNKr._adb-tls-connect._tcp` (its port changes on every reconnect; `adb mdns services` shows it), so `export ANDROID_SERIAL=adb-RFAW21MWV5T-3XuNKr._adb-tls-connect._tcp` before `./gradlew :wear:installDebug`.
- The watch drops off when its screen sleeps for a while: ask the owner to wake it, then `adb shell input keyevent KEYCODE_WAKEUP` keeps it up.
- Another PC pairs once: *Wireless debugging → Pair new device*, then `adb pair <ip>:<port> <code>` while that screen stays open (it expires when the watch dims).
- The watch records through voice recognition, like the emulator.
- Its screen sleeps after 15 s (the owner's setting) and always-on is off. The watch's UI tests (`./gradlew :wear:installDebug :wear:installDebugAndroidTest`, then `adb shell am instrument -w -r -e package io.github.klaboukes.twangle.wear io.github.klaboukes.twangle.test/androidx.test.runner.AndroidJUnitRunner`) need the screen kept on, or the tuner's screen tests fail with "No compose hierarchies": raise `settings put system screen_off_timeout` for the run and put back 15000 after. A check of always-on (`settings put global ambient_enabled 1`) puts back 0.
- Whether Twangle holds the microphone shows in the watch's log, as the mic icon follows it: `adb logcat -d | grep "IndicatorPresenter.*record_audio"` (`active=true`/`false`), beside `QuickpanelView.onDisplayChanged` (SCREEN ON/OFF).

## Emulators
- Phone: AVD `Medium_Phone_API_37.0` exists but needs the **Windows Hypervisor Platform** feature enabled.
- Wear OS: AVD `Wear_OS_Large_Round` (Wear OS 6, API 36, `system-images;android-36;android-wear-signed;x86_64`). Start it with `<sdk>/emulator/emulator.exe -avd Wear_OS_Large_Round`, then `./gradlew :wear:installDebug`. `<sdk>/cmdline-tools/latest` has `sdkmanager` and `avdmanager`; from Git Bash, write package paths with `/` instead of `;`.

## Every device
- The owner works from a laptop and a desktop, each with its own debug key, so the device's Twangle may be signed by the other machine (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). The owner's rule: during development, just uninstall it (`adb uninstall io.github.klaboukes.twangle`) and reinstall. Losing Twangle's settings and own tunings is fine.
- Leave every other setting on the device as it was, above all on the S26, the owner's daily phone: put back anything a check changes (font size, rotation, screen timeout, developer options); `scripts/ui-tests.sh` restores the rotation itself.
- **Play Protect's "Send app for a security check?"** can cover the screen after an install from this PC (seen on the tablet for the test helper `io.github.klaboukes.twangle.test`, 2026-10-27), and a covered screen fails UI tests and spoils store screenshots. The owner's standing rule, on every device and either PC: tap **Don't send** (screencap first to find it), never *Send this time* or *Always send*, and leave Play Protect itself on. Then check the run's results and redo what the dialog covered.
- `adb devices` empty or `unauthorized`: accept the USB-debugging prompt on the device and set the USB mode to *File transfer*.
- **More than one device plugged in** (a phone and the tablet, say): `adb` and the scripts stop with "more than one device". Pick one with `export ANDROID_SERIAL=<serial>` (serials above) before the script or Gradle task; `adb`, every script in `scripts/` and Gradle's device tasks all follow it.
