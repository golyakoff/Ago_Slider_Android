# TODO

## Scenarios mode (planned)

A mode where the user records a time-based sequence of coordinated motion commands and
plays it back. Example scenario:

> Over one minute, travel the full length of the slider left to right (X axis), while the
> pan axis (C) keeps the camera aimed at a fixed point 1 m in front of the slider, and the
> tilt axis (B) rises from −10° to +10° relative to the horizon.

Implications to think through:

- Timeline model: keyframes per axis (position + time), interpolation between them.
- Coordinated multi-axis moves ("aim at a fixed point" means C angle is a function of X
  position — needs simple trigonometry and slider geometry parameters).
- Firmware support: current BLE MOVE characteristic is a one-shot relative move with a
  fixed speed per axis; scenarios will likely need either streaming of intermediate
  waypoints from the app or a new firmware-side scenario/trajectory engine.
- Storing/naming/editing scenarios locally on the phone.

## UI reorganization (in progress)

Split the single device page into separate screens by purpose:

- [x] Settings — steps/current/speed configuration
- [x] Service commands — low-level per-axis relative moves (linear/angular) for motor checks
- [x] Motion — high-level motion commands (currently only Home; scenarios mode will live here)

## Cleanup backlog

- [x] Remove hardcoded GitHub PAT from `di/MainModule.kt` — now read from
  `local.properties` (`github.token`, optional) via `BuildConfig`; repo pointed at
  `golyakoff/Ago_Slider_ESP32`. **The old leaked PAT still must be revoked on GitHub and
  is still present in git history (commit f24f906..0579f34).**
- [x] Fix `utils/HashUtils.kt` package declaration → `net.agolyakov.agoslider.utils`.
- [x] Remove unused `mc_*` string resources inherited from Matrix Clock (kept ones renamed
  without the prefix); clock icon on HomeScreen replaced with a motion-control icon.
- [x] Remove the stub `DeviceRepository` — real BLE scanning in `HomeViewModel` drives the
  device list; previews use a local sample list.
- [x] Deprecated `Divider` → `HorizontalDivider` in Compose UI.
- Migrate `app/build.gradle.kts` to the new AGP DSL: the build warning
  `'fun Project.android(...)' is deprecated` comes from the compatibility mode
  (`android.newDsl=false` in gradle.properties) kept during the AGP 9 upgrade. Proper fix =
  drop that flag plus rewrite the `applicationVariants.all { }` APK-renaming block with the
  `androidComponents` variant API. Same applies to TetrisClockBLE — migrate both together.
- [x] The firmware repo (`Ago_Slider_ESP32`) now has a release workflow publishing
  `ago_slider_{debug,release}_16mb_fw.bin`; the app's asset filters updated from `4mb`
  to `16mb` accordingly, and the Service tab got a firmware-update entry point.
