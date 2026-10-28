#!/usr/bin/env python3
"""Fetch checksum-pinned upstream Android runtime binaries; requires dpkg-deb."""
import hashlib, json, pathlib, shutil, subprocess, tempfile, urllib.request
root = pathlib.Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as tmp:
    tmp = pathlib.Path(tmp)
    for package in json.loads((root / 'runtime-packages.json').read_text()):
        deb = tmp / (package['Package'] + '.deb')
        urllib.request.urlretrieve('https://packages.termux.dev/apt/termux-main/' + package['Filename'], deb)
        if hashlib.sha256(deb.read_bytes()).hexdigest() != package['SHA256']:
            raise RuntimeError('Checksum mismatch: ' + package['Package'])
        subprocess.run(['dpkg-deb', '-x', str(deb), str(tmp / 'unpacked')], check=True)
    prefix = tmp / 'unpacked/data/data/com.termux/files/usr'
    dest = root / 'app/src/main/jniLibs/arm64-v8a'
    dest.mkdir(parents=True, exist_ok=True)
    for source, name in {'bin/proot':'libproot.so', 'libexec/proot/loader':'libproot-loader.so', 'libexec/proot/loader32':'libproot-loader32.so', 'lib/libtalloc.so.2.5.0':'libtalloc.so', 'lib/libandroid-shmem.so':'libandroid-shmem.so'}.items():
        shutil.copyfile(prefix / source, dest / name)
        (dest / name).chmod(0o755)
