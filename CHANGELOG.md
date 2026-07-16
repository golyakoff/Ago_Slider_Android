# Release 0.1.4

- The device screen reconnects automatically when the slider drops the link — e.g. right
  after a firmware OTA update reboots it; no more manual reconnecting.
- The action bar shows "<slider name> <fw version>" (e.g. "My slider v0.1.3"); the
  "Slider |" prefix and the untranslatable "FW x.y.z" header column are gone.
- The app is called just "Slider", and the motor switch reads "Enable motors" /
  "Включить моторы".

# Release 0.1.3

- Fixed: rotating the screen (or switching the in-app language) no longer drops the BLE
  connection — a firmware OTA update now survives the rotation instead of freezing.
- Every card on the Settings / Service / Motion tabs got an icon in its header.
- Simpler setting titles ("Virtual limit", "StealthChop", "Units per step"), and the
  units-per-step fields show an mm/step or deg/step caption per axis, following the
  axis-unit choice live.

# Release 0.1.2

- UI switched to English with a Russian translation; the in-app language follows the
  system locale by default and can be changed from the Home screen.
- Settings tab reworked into typed controls that save on change, with an explanation of
  what a step rate means.
- Service tab moves are entered as a distance in axis units instead of a raw step count.
- Device state (motor enable, limit switches, power, battery) is read right on connect,
  and the device header is laid out in fixed columns.
- Home screen shows the installed app version and a notice when a newer release is
  available.
- Fixed: the Save button on a Settings card now disables after a successful save —
  every setter re-reads its characteristic so the app state resyncs with the device.

# Release 0.1.1

- Faster firmware OTA upload: chunk size now fits a single BLE write (MTU-3) instead of
  long writes, and the fixed 30 ms per-chunk delay is removed — flow control relies on
  the firmware (v0.1.2+) acknowledging each chunk after it is written to flash.

# Release 0.1.0

- Device screen split into Motion / Service / Settings tabs with a shared status header.
- Firmware update entry on the Service tab (current version + update check).
- Firmware update assets switched to `*_16mb_fw.bin` from the Ago_Slider_ESP32 releases.
- Build upgraded to Gradle 9.6.1 / AGP 9.2.1; Matrix Clock leftovers cleaned up
  (hardcoded GitHub PAT removed, stub device list dropped, unused resources pruned).

# Release 0.0.1

[x] Intial commit.

**Full Changelog**: https://github.com/golyakoff/Ago_Slider_Android/commits/v0.0.1
