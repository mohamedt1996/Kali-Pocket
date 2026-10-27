# Kali Pocket standalone test build

Based on Tiny Container v4.2.2 (GPLv3); upstream copyright and attribution are retained. The embedded PRoot and AVNC runtime runs inside this APK. No separate Termux or VNC app is needed.

Requires ARM64 Android 9 or newer. First launch extracts the bundled verified Kali minimal filesystem, then uses Kali apt repositories to install XFCE, TigerVNC, D-Bus and Python. Keep the app open and online. Allow several GB of free storage. Setup output appears in the built-in terminal. Once the Unix-domain VNC socket is ready the app opens the desktop; subsequent launches reuse installed packages. Initial installation can take many minutes. Do not assume the app has hung while apt is running.

Guest Tools shortcuts expose setup retry, installation logs and LAN/LLM bridge configuration. Configure an OpenAI-compatible base URL (for Ollama, http://YOUR_PC_IP:11434/v1), model and optional API key. LAN access is opt-in and uses a bearer token. Keep the token private and use trusted Wi-Fi/VPN for the plain HTTP bridge on port 8765. VNC uses a private Unix socket, not a LAN TCP listener.

This is an experimental device-test build. It has not been booted on a physical Android device here. Desktop installation, PRoot compatibility, touch, keyboard, audio and Android battery/process behavior need device testing. Rootless Kali cannot supply kernel modules or Wi-Fi monitor mode. The graphical session currently runs as emulated root inside the app sandbox.

Build: install JDK 21, Android SDK 37.0, NDK 28.2.13676358, CMake 3.22.1, curl, tar, xz, zstd and unzip. Run `bash kali/scripts/prepare.sh`, then `./gradlew assembleDebug`. The preparation script fails if the upstream mutable rootfs changes; review a new image before updating the pinned digest. Output is app/build/outputs/apk/debug/app-debug.apk.

Native libraries are from Tiny Container's v4.2.2 release; their source is identified in the upstream README. The rootfs contains Kali packages under their respective licenses. Distribute corresponding sources and notices as required when publishing binaries. This project is independent of Kali, Termux and Tiny Container.
