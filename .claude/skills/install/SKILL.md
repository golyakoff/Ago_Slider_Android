---
name: install
description: Build the debug APK and install it on the connected Android device via adb. Use when asked to run, install, or test the app on a phone.
---

# Build & install on device

`JAVA_HOME` is not set globally on this machine — use Android Studio's bundled JBR:

```bash
cd /c/xAndroid/Projects/AgoSlider
export JAVA_HOME="C:\\Program Files\\Android\\Android Studio\\jbr"
./gradlew installDebug
```

`adb` lives in the Android SDK platform-tools; if not on PATH:
`C:\Users\ago\AppData\Local\Android\Sdk\platform-tools\adb.exe` (verify the path
before relying on it). Useful commands:

- `adb devices` — check the phone is connected and authorized
- `adb shell am start -n net.agolyakov.agoslider/.MainActivity` — launch the app
- `adb logcat -s BluetoothService AgoSliderManager OTA` — filtered app logs

Testing against real hardware requires the AGO Slider device powered on and
advertising over BLE (device name "AGO Slider"); Bluetooth and location permissions
must be granted on first run.
