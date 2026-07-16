# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

AgoSlider Android app (`net.agolyakov.agoslider`): a Kotlin/Jetpack Compose BLE client for the
AGO Slider firmware (ESP32-based 3-axis camera slider, see the companion `Ago_Slider_ESP32`
firmware repo). It scans for, connects to, and controls the device over its custom BLE GATT
service (`0xFE95`), and can push new firmware to it via BLE OTA using .bin assets pulled from a
GitHub Releases feed.

## Build / test commands

Standard Gradle Android project (Kotlin DSL, Gradle 9.6.1, AGP 9.2.1, Kotlin 2.2.10,
min/target/compile SDK 28/36/36, JVM target 11). The Gradle daemon JVM (JDK 21) is provisioned
automatically via the foojay toolchain resolver (`gradle/gradle-daemon-jvm.properties`);
`gradle.properties` carries AGP 9 compatibility flags (`android.builtInKotlin=false`,
`android.newDsl=false`, etc.) that keep the legacy variant API and kapt working — don't remove
them without migrating `applicationVariants.all` in `app/build.gradle.kts` first.

```
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (unsigned locally; CI signs with secrets)
./gradlew test                   # JVM unit tests (app/src/test)
./gradlew connectedAndroidTest   # instrumented tests on a connected device/emulator (app/src/androidTest)
```

`JAVA_HOME` is not set globally on this machine — export it before gradle runs:
`export JAVA_HOME="C:\\Program Files\\Android\\Android Studio\\jbr"`.

CI (`.github/workflows/release.yml`) builds a signed release APK and publishes a GitHub Release
whenever a `v*.*.*` tag is pushed; the changelog entry is read from `CHANGELOG.md` (format:
`# Release X.Y.Z` heading followed by bullet lines). See also the project skills: `release`
(version bump + tagging + CI) and `install` (build + adb install on a device).

## Architecture

Standard MVVM + Hilt DI + single-Activity Compose Navigation, layered as
`ui` (Compose screens/viewmodels) → `service`/`data.repository` → `data.remote`/`data.local`.

- **`service/bluetooth/`** — all BLE code, built on the Nordic `no.nordicsemi.android:ble`
  library.
  - `AgoSliderManager` (extends Nordic's `BleManager`) owns the GATT session: declares every
    characteristic UUID for service `0xFE95` (mirrors the firmware's GATT table 1:1 — UUIDs
    `0xF0xx` control/status, `0xF02x` power, `0xF03x` config, `0xF09x` OTA), verifies they're all
    present in `isRequiredServiceSupported`, and exposes one `read*`/`write*` method per
    characteristic. Byte layouts (little-endian int32 for MOVE, uint16 pairs for
    current/speed/accel, packed bit-flags for unit/limit/StealthChop/invert) must stay in sync
    with `app_config.h`/`ble.h` in the firmware repo — if either side's wire format changes, the
    other must change too.
  - `BluetoothService` (Hilt `@Singleton`) is the app-facing façade: wraps `AgoSliderManager` in
    Kotlin `StateFlow`s (one per characteristic/status) that Compose screens observe directly, and
    exposes `connect`/`disconnect`/`setXxx` methods that both write a characteristic and
    immediately re-read it to resync app state with the device.
  - `handlers/*ReadCharacteristicHandler` — one class per readable/notifiable characteristic,
    each implementing `ReadCharacteristicHandler.onReadCharacteristicCallback` to decode the raw
    `Data` payload into the corresponding `BluetoothService` `StateFlow`. Adding a new
    characteristic means adding a handler here, wiring it into `AgoSliderManager` and
    `BluetoothService`, and adding the UUID constant to `AgoSliderManager.Companion`.
  - On `ConnectionObserver.onDeviceReady`, `BluetoothService` calls
    `readAllConfigurationCharacteristics()` to pull the device's current config; notify-only
    characteristics (MOT_EN, HOME, LIMIT, battery, power, VERSION) are subscribed once in
    `AgoSliderManager.initialize()`.

- **OTA update flow** — `FirmwareRepository` orchestrates
  `GithubRepository` (Retrofit/OkHttp client for the GitHub Releases API) → download firmware
  `.bin` asset → SHA-256/size validation (`HashUtils`) → chunked write over
  `sendOtaControl`/`sendOtaData` (mirrors the firmware's OTA_CONTROL/OTA_DATA byte protocol) →
  reconnect-and-verify-version polling loop. `BluetoothService.enterOtaUpdateMode()` /
  `exitOtaUpdateMode()` suppress normal disconnect handling while a transfer is in flight.
  Progress/state is modeled as a `FirmwareRepository.UpdateState` sealed class consumed by
  `FirmwareViewModel`.

- **App update check** — `AppUpdateRepository` watches the releases of the *app's own* repo
  (`golyakoff/Ago_Slider_Android`), not the firmware's; `HomeViewModel` polls it every 10 minutes
  and the Home screen's bottom plaque shows the installed version plus, when a newer tag exists, a
  button opening the release page in a browser. There is no in-app APK install — only firmware is
  updated from inside the app.

- **Navigation** (`navigation/NavGraph.kt`, `Screen.kt`) — three Compose destinations: `Home`
  (scan/select device) → `Device` (main control screen, receives `AgoSliderDevice` via the nav
  back-stack `SavedStateHandle`, not as a route argument) → `FirmwareUpdate`. The Device screen
  itself is split into three bottom-nav tabs (`DeviceTab` enum in `DeviceScreen.kt`, tab content
  in `MotionTab.kt` / `ServiceTab.kt` / `SettingsTab.kt`): Motion (high-level commands — Home
  now, scenarios mode later, see `TODO.md`), Service (low-level per-axis relative moves + limit
  switch / power status), Settings (all device config). The shared header (device identity,
  connection state, battery, global motor-enable switch) stays visible on every tab.

- **Device discovery** — `HomeViewModel` does live BLE scanning (filtered by device name
  "AGO Slider") and enriches results with user-assigned friendly names from preferences.

- **Persistence** — `AgoSliderPreferences` (SharedPreferences-backed, implements
  `domain.repository.PreferencesRepository`) stores only user-assigned friendly names per MAC
  address; there is no other local persistence (no Room/DataStore).

## Known issues to be aware of

- A GitHub PAT was previously hardcoded in `di/MainModule.kt` and **remains in git history**
  (commits before the cleanup) — it must be revoked on GitHub. The token is now optional and
  read from `local.properties` (`github.token`) via `BuildConfig.GITHUB_TOKEN`.
- The firmware repo (`golyakoff/Ago_Slider_ESP32`) publishes releases via its own tag-driven
  workflow with assets `ago_slider_{debug,release}_16mb_fw.bin`; the update check in
  `FirmwareRepository` filters on the `*_release_16mb_fw.bin` suffix — keep the two in sync.
