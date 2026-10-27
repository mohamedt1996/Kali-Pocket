# Kali Pocket

An Android companion for **Kali NetHunter Rootless**, with KeX graphical desktop controls and a local/LAN API for connecting an LLM. Native Java Android UI; Python standard-library bridge runs inside Termux. No root required.

This is a working prototype, not a standalone bundled Kali distribution. It requires Termux, a NetHunter Rootless installation, and a VNC/KeX viewer. The app guides you through these prerequisites. It does not emulate a separate Linux kernel or include Kali packages in the APK.

## Install on your phone

1. Install the debug APK from `dist/`, or build it below. Requires Android 8 or newer.
2. Install [Termux from F-Droid](https://f-droid.org/packages/com.termux/) or official Termux GitHub releases. Follow the [official NetHunter Rootless installation guide](https://www.kali.org/docs/nethunter/nethunter-rootless/). An ARM64 phone with several GB of free storage is recommended; distribution support depends on the device.
3. In Termux, run `nh` to confirm Kali works. Exit back to Termux, then run `nh kex passwd` to set the desktop password.
4. Enable the Termux integration:

   ```sh
   mkdir -p ~/.termux
   echo 'allow-external-apps=true' >> ~/.termux/termux.properties
   termux-reload-settings
   ```

5. Open **Kali Pocket → Setup**. Enter your LLM base URL and model, save, then tap **Install / restart bridge**. Grant the Termux run-command permission when prompted. This installs Python through Termux's package manager, writes the bundled bridge and a private configuration under `~/.kali-pocket`, and runs the bridge in a visible Termux terminal. Review that terminal if anything fails.
6. Return to Kali Pocket and tap **Check connection**. In **Desktop**, tap **Start desktop**. Install NetHunter KeX or a compatible VNC viewer and connect to `127.0.0.1:5901` with your KeX password. The Open VNC viewer button works with clients that support `vnc://` links; otherwise enter the address manually.

Keep Termux running. Android battery restrictions and process limits can terminate Kali or the bridge. Exempt Termux from battery optimization when needed. Restart the bridge after saving settings or rotating the token. Session controls target the default non-root NetHunter KeX session on display `:1`; existing custom KeX configurations may need adjustment.

## Connect an LLM on the same Wi-Fi

For an Ollama server on a Linux workstation, make it listen on the LAN and pull a model:

```sh
# For a manually launched Ollama instance; stop an existing service first if it uses this port.
OLLAMA_HOST=0.0.0.0:11434 ollama serve
# In a second terminal:
ollama pull llama3.2
```

If using a managed Ollama service, configure `OLLAMA_HOST` in the service instead. Allow the phone through your workstation firewall on TCP 11434. In Kali Pocket set the base URL to `http://YOUR_WORKSTATION_IP:11434/v1` and model to `llama3.2`, save and restart the bridge. For another OpenAI-compatible server, use its `/v1` base URL, model identifier and optional API key.

The phone calls the Python bridge, which forwards chat to `/chat/completions` on your chosen server. Chat is text only and does **not execute model output**. Conversation history stays in app memory and is lost on process termination. The selected server receives your messages and may apply its own retention policy.

## Reach the phone from another device

Turn on **Allow bridge access from my LAN**, save and restart. This changes the bridge bind address from `127.0.0.1` to `0.0.0.0`. Find your phone's Wi-Fi IP in Android's network settings, and use **Copy LAN curl example** to obtain the token header. Protect that token as a password.

```sh
export POCKET_URL=http://PHONE_WIFI_IP:8765
# Set POCKET_TOKEN to the private token copied from Setup.
curl -H "Authorization: Bearer $POCKET_TOKEN" "$POCKET_URL/v1/status"

curl -X POST -H "Authorization: Bearer $POCKET_TOKEN" \
  -H 'Content-Type: application/json' -d '{}' "$POCKET_URL/v1/session/start"

curl -X POST -H "Authorization: Bearer $POCKET_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Explain the Linux filesystem"}]}' \
  "$POCKET_URL/v1/chat"
```

| Route | Method | Behavior |
| --- | --- | --- |
| `/v1/status` | GET | NetHunter presence, KeX/VNC availability, last session error and LLM configuration without secrets |
| `/v1/session/start` | POST | Start KeX asynchronously; send `{}` |
| `/v1/session/stop` | POST | Stop KeX asynchronously; send `{}` |
| `/v1/chat` | POST | Forward `messages` to the configured LLM; returns `reply` |

Every route requires `Authorization: Bearer TOKEN`. Successful desktop actions return HTTP 202; poll status to observe the result. Missing/incorrect credentials return 401. There is no arbitrary command-execution API. KeX desktop access uses a separate VNC password and remains localhost-only by default. For desktop access from another device, use an SSH/VPN tunnel to port 5901; the app does not open VNC to the LAN.

LAN mode uses HTTP. Use trusted Wi-Fi or a VPN; put an HTTPS reverse proxy in front of the API if transport encryption is required. Do not port-forward this prototype onto the public internet. Wi-Fi client isolation can prevent devices reaching each other. Some Android versions require additional local-network permission as their platform rules evolve.

## Build

Requires JDK 17+, Android SDK platform 35 and Gradle 8.9 (wrapper included):

```sh
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. This is a debug-signed prototype; configure your own release signing before distribution. No native libraries are bundled, so the APK itself is architecture-independent. NetHunter's rootfs architecture must match your phone.

Run bridge integration tests (Python 3.9+):

```sh
python3 -m unittest discover -s tests -v
```

The tests use temporary localhost HTTP servers, a mock OpenAI-compatible LLM and mocked NetHunter processes. They cover authentication, request limits, message validation, fixed desktop commands and proxying. They do not establish that Kali/KeX works on a particular Android device.

`bridge/bridge.py` is the source; keep `app/src/main/assets/bridge.py` synchronized after editing. Android has no third-party runtime dependencies; the bridge uses Python's standard library. The app disables Android backups for its settings; API keys are stored in app-private preferences and the Termux-private config (mode restricted by `umask 077`). Root or device compromise can read these settings.

## Limits

Rootless Kali shares Android's kernel. Wi-Fi monitor mode, packet injection, kernel modules, USB gadget features and unrestricted hardware access require device-specific root/kernel support. This app does not implement those features. The bridge is a foreground Termux process, not an Android boot service. It must be restarted after reboot or process termination. It does not install Kali automatically or render VNC itself.

Kali Pocket is independent of Kali Linux, Offensive Security and Termux.
