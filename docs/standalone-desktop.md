# Standalone Kali desktop implementation

Goal: a fresh install downloads and installs a verified Kali environment, then shows an interactive desktop inside the app. Later app launches start or reconnect to the desktop automatically. No separate Termux or VNC application should be required.

## Required components

1. An Android-compatible userspace runtime shipped with the app, including an appropriately licensed ARM64 proot executable and the libraries it needs. Android 10+ restricts executing files from app-writable storage, so executable delivery must comply with the target Android version, and must be tested on actual devices.
2. A first-run installer that downloads a Kali ARM64 root filesystem and verifies it against authenticated checksums or signatures, safely extracts it, and reports progress and recoverable errors. Package installation must configure a desktop, VNC server, and a non-root user. Disk-space and interrupted-install recovery need explicit handling.
3. A foreground lifecycle service that starts the environment and desktop, manages failures and reconnects, and accounts for Android process and battery limits. Startup must not expose an unauthenticated VNC service to the LAN.
4. An in-app VNC client that implements authentication, framebuffer updates and resizing, touchscreen pointer and scroll gestures, keyboard input, clipboard and connection recovery. A usable mobile desktop needs device testing, especially for screen scaling and gesture behavior.
5. Retain the existing authenticated LAN API and LLM integration inside the standalone runtime, with LAN access disabled by default.

## Acceptance checks

- Clean install on a supported ARM64 phone: complete first-run setup without installing other apps or entering terminal commands.
- Relaunch: desktop starts or reconnects without manual steps; touch, scroll, keyboard and screen rotation work.
- Kill and restart the app, interrupt a download, deny permissions, exhaust storage, reboot, and verify clear recovery paths.
- Verify that VNC stays local, LAN API authentication works, and a configured LAN LLM responds.
- Check Android versions and CPU architectures on real hardware before claiming broad compatibility.

Current project status: the existing APK is a Termux/KeX companion and does not implement these components. Do not present its APK as the standalone version.
