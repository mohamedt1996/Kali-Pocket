#!/usr/bin/env bash
set -euo pipefail
project=$(cd "$(dirname "$0")/../.." && pwd)
cache="$project/.guest-build"
mkdir -p "$cache" "$project/app/src/main/assets"
curl -fL --retry 3 https://images.kali.org/nethunter/rootfs/kali-nethunter-rootfs-minimal-arm64.tar.xz -o "$cache/rootfs.tar.xz"
printf '%s  %s\n' 96aeced81a857a987715a14417af05c2934e3e19052d6151c398a1c215448b60 "$cache/rootfs.tar.xz" | sha256sum -c -
root=$(mktemp -d "$cache/root.XXXXXX")
trap 'rm -rf "$root"' EXIT
tar -xJf "$cache/rootfs.tar.xz" -C "$root" --strip-components=1 --no-same-owner --exclude='kali-arm64/dev/*'
cp -a "$project/kali/overlay/." "$root/"
mkdir -p "$root/dev"
printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > "$root/etc/resolv.conf"
tar -C "$root" --transform='s,^\./,,' -I 'zstd -T2 -3' -cf "$project/app/src/main/assets/rootfs.tar.zst" .
curl -fL --retry 3 https://github.com/Cateners/tiny_container/releases/download/v4.2.2/jniLibs.zip -o "$cache/jniLibs.zip"
unzip -qo "$cache/jniLibs.zip" -d "$project/app/src/main"
