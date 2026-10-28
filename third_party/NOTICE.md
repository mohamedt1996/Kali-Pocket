# Third-party components

- **PRoot**, Termux upstream version 5.1.107.101, GPL-2.0-or-later. Source is included in `proot/` at tag v5.1.107.101 (b27d81a68d765f3462e807f4a14e01a03f8e89ea), from https://github.com/termux/proot. See its COPYING and build files. The packaged Android executable and loaders come from the pinned Termux package in `runtime-packages.json`.
- **talloc 2.5.0**, LGPL-3.0-or-later, source and license included in `sources/talloc-2.5.0.tar.gz`, obtained from https://www.samba.org/ftp/talloc/.
- **libandroid-shmem 0.7**, BSD-3-Clause, source and license in `sources/libandroid-shmem-0.7.tar.gz`, from https://github.com/termux/libandroid-shmem.
- Runtime Android package recipes are maintained at https://github.com/termux/termux-packages under packages/proot, packages/libtalloc and packages/libandroid-shmem. Pinned recipe snapshots are included in `termux-recipes/`.
- **Apache Commons Compress 1.27.1**, Apache-2.0: https://commons.apache.org/proper/commons-compress/.
- **XZ for Java 1.10**, 0BSD: https://tukaani.org/xz/java.html.
- **Gradle wrapper 8.9**, Apache-2.0: https://gradle.org/.

Termux package infrastructure supplies Android-compatible upstream binaries; no Termux application, IPC service, or Tiny Container code is used by this app.
