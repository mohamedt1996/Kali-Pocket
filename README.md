# Kali Pocket — independent ARM64 test build

A standalone Android application with an embedded upstream PRoot runtime, its own touchscreen RFB viewer, and automatic setup of the official full Kali NetHunter ARM64 root filesystem. No separate Termux, Tiny Container, VNC viewer, or rooted Android device is required. This branch replaces the earlier companion and Tiny Container prototypes.

## Install and run

Download the `kali-pocket-native-pad7` ZIP from the latest successful **Native Android APK** GitHub Actions run, extract it, and install the APK. This is a debug-signed test build. If Android rejects an update from an older prototype with a different signing key, uninstall that prototype first (this deletes its app data).

Requires ARM64 Android 8 or newer, Internet access for setup, and at least **16 GiB free storage**. First launch downloads approximately **1.8 GB** and extracts the full Kali filesystem. Allow notifications and keep the app open during setup. Setup verifies SHA-256 before extraction; an interrupted extraction can reuse a complete verified download. An interrupted download starts again. Later launches reuse the installed filesystem and automatically start/open the desktop.

The Xiaomi Pad 7 profile uses a 1800×1200 desktop, 144 DPI, aspect-preserving scaling, and a 30 fps update cap. Touch supports click/drag; toolbar controls provide right click, scrolling, keyboard, Ctrl, Tab and Escape. Physical keyboard input is supported. This is a software-rendered desktop: GPU acceleration and device-specific performance have not been verified.

**Validation:** APK compilation, JVM installer tests, Android lint, and extraction of the actual full Kali image passed on the build host. Android/HyperOS runtime boot, touchscreen behavior, and suspend/resume still require testing on a physical device. Open **Guest Tools → logs** if startup fails.

## LAN and LLM

Under Guest Tools configure an OpenAI-compatible endpoint, for example `http://192.168.1.20:11434/v1`, model, and optional API key. Stop and restart the desktop to apply settings. The built-in chat uses that server. Enable LAN access to reach the authenticated bridge at `http://TABLET_IP:8765`. Copy its bearer token from Guest Tools.

- `GET /v1/status` returns desktop status.
- `POST /v1/chat` accepts `{"messages":[{"role":"user","content":"Hello"}]}`.
- Send `Authorization: Bearer YOUR_TOKEN` for both routes.

LAN access is off by default. HTTP should only be used on trusted Wi-Fi or through a VPN. The bridge does not execute arbitrary shell commands. VNC stays bound to localhost with a random private password.

## Build

JDK 17, Android SDK platform 35 and Gradle wrapper 8.9:

```
./gradlew assembleDebug testDebugUnitTest lintDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds and packages the APK without downloading the 1.8 GB filesystem on the runner. `scripts/fetch-runtime.py` retrieves and verifies the pinned upstream PRoot/talloc/shmem binaries already included in `jniLibs` (Python 3 and dpkg-deb required).

The image URL and checksum are pinned in `RootfsInstaller.java`. Kali's `current` URL is mutable: if upstream replaces it, setup deliberately rejects the changed image until the URL/hash are reviewed and updated. Extraction is staged before promotion; archive traversal and symlink-parent escapes are rejected.

PRoot supplies userspace root emulation, not Android kernel privileges. Kernel-dependent features such as Wi-Fi monitor mode and kernel modules are unavailable through this runtime. Android may terminate background processes; reopen the app to restart. Data remains in app-private storage until uninstall or clear-data.

## Sources and licenses

Original app code is GPL-3.0-or-later. See `LICENSE` and `third_party/NOTICE.md` for upstream runtime sources and dependency licenses. Kali packages retain their respective licenses. This is an independent project, not an official Kali or Xiaomi product.
