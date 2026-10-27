#!/bin/bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
mkdir -p /var/log /root/.config /tmp/runtime-root
chmod 700 /tmp/runtime-root
export XDG_RUNTIME_DIR=/tmp/runtime-root
if [ ! -f /opt/kali-pocket/desktop-ready ]; then
    echo 'Installing Kali desktop. Keep the app open and connected to the Internet.'
    # Android guests do not run systemd; prevent package scripts starting services.
    printf '#!/bin/sh\nexit 101\n' > /usr/sbin/policy-rc.d
    chmod 755 /usr/sbin/policy-rc.d
    apt-get update 2>&1 | tee /var/log/kali-pocket-setup.log
    apt-get install -y --no-install-recommends xfce4 xfce4-terminal tigervnc-standalone-server dbus-x11 python3 ca-certificates 2>&1 | tee -a /var/log/kali-pocket-setup.log
    command -v Xtigervnc
    command -v startxfce4
    touch /opt/kali-pocket/desktop-ready
fi
if [ -S /tmp/.tiny.vnc ]; then
    echo 'Desktop is already running.'
    exit 0
fi
Xtigervnc :6 -geometry 1280x720 -depth 24 -rfbport -1 -rfbunixpath /tmp/.tiny.vnc -SecurityTypes None -alwaysshared -pn > /var/log/kali-pocket-vnc.log 2>&1 &
for attempt in $(seq 1 100); do
    [ -S /tmp/.tiny.vnc ] && break
    sleep 0.1
done
[ -S /tmp/.tiny.vnc ] || { cat /var/log/kali-pocket-vnc.log; exit 1; }
DISPLAY=:6 dbus-run-session -- startxfce4 > /var/log/kali-pocket-desktop.log 2>&1 &
echo 'Kali desktop started. Open the Kali desktop tab.'
