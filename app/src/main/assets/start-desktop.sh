#!/bin/bash
set -euo pipefail
export HOME=/root USER=root DISPLAY=:1 LANG=C.UTF-8
mkdir -p /root/.config/kali-pocket /tmp/runtime-root
chmod 700 /root/.config/kali-pocket /tmp/runtime-root
export XDG_RUNTIME_DIR=/tmp/runtime-root
for binary in Xtigervnc vncpasswd dbus-run-session startxfce4; do
 command -v "$binary" >/dev/null || { echo "Required desktop program missing: $binary"; exit 1; }
done
printf '%s\n' "$1" | vncpasswd -f > /root/.config/kali-pocket/vncpass
chmod 600 /root/.config/kali-pocket/vncpass
# Only remove a lock when its X server is no longer present.
if [ -f /tmp/.X1-lock ]; then
 old_pid=$(tr -d ' ' < /tmp/.X1-lock)
 if ! kill -0 "$old_pid" 2>/dev/null; then rm -f /tmp/.X1-lock /tmp/.X11-unix/X1; fi
fi
Xtigervnc :1 -geometry 1800x1200 -dpi 144 -depth 24 -localhost yes -rfbport 5901 -SecurityTypes VncAuth -rfbauth /root/.config/kali-pocket/vncpass -AlwaysShared -MaxDisconnectionTime 0 &
xpid=$!
trap 'kill "$xpid" 2>/dev/null || true' EXIT
sleep 2
dbus-run-session -- startxfce4 &
wait "$xpid"
