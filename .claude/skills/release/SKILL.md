---
name: release
description: Publish an AgoSlider app release - bump versionCode/versionName, update changelog, tag vX.Y.Z, push, and monitor the GitHub Actions build. Use when asked to release or publish a new app version.
---

# Release the Android app

Releases are built by `.github/workflows/release.yml` on a `v*.*.*` tag push: CI
builds a signed release APK (keystore comes from repo secrets) named
`ago-slider-release-vX.Y.Z.apk` and publishes a GitHub Release with notes taken
from `CHANGELOG.md`.

## Release steps

1. Bump **both** `versionCode` (integer, +1) and `versionName` ("X.Y.Z") in
   `app/build.gradle.kts` — the APK filename and release come from `versionName`,
   and it should match the tag without the `v` prefix.
2. Add a section to `CHANGELOG.md` — the workflow's awk step expects the heading
   format `# Release X.Y.Z` (with `#`, without `v`). Note: the original 0.0.1 entry
   lacks the `#`, that format will NOT be picked up.
3. Local build sanity (`JAVA_HOME` is not set globally — use Android Studio's JBR):
   ```bash
   export JAVA_HOME="C:\\Program Files\\Android\\Android Studio\\jbr"
   ./gradlew assembleRelease
   ```
   (unsigned locally — only CI has the signing secrets; success is what matters).
4. Commit and push `master`.
5. Tag and push (git author must match `c:\git.txt` — Andrey Golyakov
   <golyakoff@yandex.ru>):
   ```bash
   git tag -a vX.Y.Z -m "Release vX.Y.Z"
   git push origin master vX.Y.Z
   ```

## Monitor CI

`gh` CLI is not installed. Use the GitHub API with the token from the git
credential manager:

```bash
TOKEN=$(printf "protocol=https\nhost=github.com\n" | git credential fill | grep "^password=" | cut -d= -f2)
curl -s -H "Authorization: token $TOKEN" \
  "https://api.github.com/repos/golyakoff/Ago_Slider_Android/actions/runs?per_page=3" \
  | python -c "import json,sys; [print(r['id'], r['head_branch'], r['status'], r['conclusion']) for r in json.load(sys.stdin)['workflow_runs']]"
```

## Coupled with the firmware repo

If the release includes BLE protocol or OTA changes, remember the wire format must
stay in sync with `C:\xMC\Ago_Slider_ESP32` (see CLAUDE.md). The firmware update
check downloads the firmware release asset ending in `_release_16mb_fw.bin` from
`golyakoff/Ago_Slider_ESP32` — if the firmware asset naming changes, update the
filters in `FirmwareRepository.kt` / `GithubRepository.kt` in the same release.
