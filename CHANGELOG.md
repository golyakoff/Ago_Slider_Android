# Release 0.1.8

- The "Focus on subject" card is set up by dialling rather than typing — a keyboard used to
  cover the buttons you aim with. X travel defaults to the longest pass the rail allows and
  cannot be dragged beyond it; the shot length is picked as a range first, then a slider.
- The three aim points are shown as blocks holding what each recorded. Tapping one sends the
  whole rig back to it, so the camera returns to that exact framing rather than just that
  position along the rail.
- Vertical tilt is no longer typed: B is recorded at each aim, and the pass tilts from the
  first point's angle to the third's.
- Homing several axes at once now brings all of them to zero. Previously every axis but the
  last was left standing on its endstop.
- Calibrating an axis measured in degrees gives it limits of half its travel either side of
  zero, which is where a rotary axis actually sits.
- Homing is a card of its own, and the requested/homed rows show the same indicators as the
  endstops instead of reading Yes/No.
- Requires firmware 0.1.6 or newer.

# Release 0.1.7

- New "Focus on subject" mode on the Motion tab: the slider travels along X while turning C
  to keep a subject centred, with an optional slow vertical tilt. Aim the camera at the
  subject from three points along the rail and the app works out where the subject stands
  and how far away it is — no need to judge the distance by eye. The run itself happens on
  the slider, so the phone can be disconnected, closed or restarted while it films, and
  reconnecting shows how far it got.
- Settings are grouped into Modes, Motion and Constants, and the labels drop the "axis"
  qualifier the per-axis rows already imply.
- The Service tab gained a Reset beside Move, shows endstops as indicators instead of raw
  text, and plots the session's voltage, current and power over time.
- Coordinates survive a reconnect: the slider is asked whether an axis is still anchored to
  its endstop rather than the app assuming the answer.
- Requires firmware 0.1.5 or newer.

# Release 0.1.6

- Endstop calibration is now run by the device (firmware 0.1.4 or newer): the app sends
  one command and follows the phases the slider reports. The previous version measured
  the span over Bluetooth, which could miss the sensors' millisecond-long pulses and
  drive an axis into its hard stop — on the long X rail that inflated the measured
  travel by about 50 mm.
- Coordinates now come from the device itself and are notified while it moves, so the
  displayed position no longer drifts away from reality on stalls or forced stops.
- An axis keeps its coordinate when a limit switch is touched instead of asking for a
  new homing run.

# Release 0.1.5

- Virtual axis coordinates now show in the device header (angles displayed as
  −180…180).
- New per-device positioning settings — home offsets and per-axis min/max
  limits — with moves clamped to the configured soft limits.
- Automatic endstop calibration added to the Service tab.
- Limits are re-established after a reconnect by homing (CNC-style validity
  rules).

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
