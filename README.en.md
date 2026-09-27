# Tiny MC Server

**English** | [简体中文](README.md)

A pure on-device launcher for the Minecraft **Java Edition server**. No client-side rendering, no Termux integration, no root required.

- Package name: `dev.tinymcserver.app`
- Target ABI: `arm64-v8a` (the bundled JRE is aarch64)
- Requirement: **Android 8.0 (API 26) or newer** (`minSdk 26` / `targetSdk 28`, rationale below)
- Current version: `1.0.0`

> **Why is `targetSdk` 28?**
> Since Android 10 (API 29), apps with `targetSdk >= 29` are forbidden from calling `exec()` on files inside their
> **private data directory** (the W^X restriction; see the Android 10 behavior change *execute-permission*).
> The bundled JRE must execute `java` from the private directory, so we follow Termux's compatibility approach and
> keep `targetSdk = 28` (while `compileSdk` stays at 34).
> If publishing to Google Play becomes necessary (which requires `targetSdk >= 34`), the workaround is to ship the
> JRE executables inside `jniLibs` and run them from `nativeLibraryDir`.

## Features

- One-tap instance creation: pick a flavor (Vanilla / Paper / Purpur / Folia) and an MC version; the app automatically matches and extracts the bundled JRE and downloads the matching server jar.
- Console: live logs, command input, readiness check, crash diagnostics.
- File editing: Chinese labels for `server.properties`, read-only browsing and text editing, binary detection.
- Plugin manager: search and one-tap install from Modrinth (with integrity checks, progress and cancel).
- Backup / restore, player management (whitelist / OP / ban).
- Keep-alive: foreground service + WakeLock + WifiLock + battery-optimization whitelist guidance.
- Built-in **Skin Forge**: deterministically generate a 64×64 Minecraft Java skin from a "seed + style", exportable as PNG.

## Getting started

1. Open the app → tap **+** at the bottom right.
2. Enter a name, choose a flavor (Vanilla / Paper / Purpur / Folia), choose an MC version (the JRE is matched automatically), set memory → accept the EULA.
3. Tap **"Create & download server"**: the app **automatically** extracts the matched JRE and downloads the server jar from the official source (with a progress dialog), then opens the console.
4. Tap **Start** in the console. If a download is interrupted, the "Readiness check" card at the top of the console lets you retry the JRE / server individually, or run "Prepare all".

## Where files live (all inside the app's private directory)

```
/data/data/dev.tinymcserver.app/
  files/jre/jre{major}/       ← extracted JRE home (bin/java, lib/modules, libjvm.so, …)
  files/instances/<id>/        ← server.jar, eula.txt, server.properties, plugins/, world/, backups/
```

No storage permission is required; nothing is written to shared storage.

> Why the JRE is "extracted": Android **cannot execute binaries inside an APK**, so `java` must be a real
> executable file on disk. The JRE in the APK assets is therefore extracted to the private directory before running.

## Architecture

1. **Android UI layer** (Jetpack Compose): instance list, creation wizard, console, file editor, settings, player management, backup, plugins, Skin Forge.
2. **Server Manager layer**: instance model, version resolution, JRE matching, launch-argument assembly, lifecycle, log parsing, auto-restart.
3. **Runtime layer**: the bundled JRE, launching `java -jar xxx.jar nogui` via `ProcessBuilder`, fully headless.
4. **Native/OS layer**: foreground service, WakeLock/WifiLock, SAF export, network listeners, JRE `.so` dependencies.

## Bundled JRE

Three versions of **Android bionic OpenJDK** (aarch64) are bundled and matched to MC versions.
They are licensed under **GPLv2 + Classpath Exception**; see [`NOTICE`](NOTICE) for copyright and origin.
The full license originals ship with the binaries inside the `legal/` directory of each archive.

**Origin**: the `openjdk-17 / 21 / 25` (aarch64) binary packages from the **official Termux repository** (`packages.termux.dev`).
This project applies **heavy trimming and a self-implemented replacement** to reduce size, drop unused modules,
and keep dependencies clean:

**① Trimming** — removed non-runtime content: `jmods/`, `demo/`, `man/`, `include/`, `ct.sym`, and all `bin/` tools except `java`;
plus the native libraries a server never uses:

- Graphics / audio: `libjpeg`, `liblcms2`, `libasound`, `libandroid-sysv-semaphore`, `libjavajpeg`, `liblcms`, `libjsound`
- Debug / agent: `libinstrument` (`-javaagent`), `libjdwp` (remote debugging), `libiconv`

**② Self-implemented replacements** — the following two libraries were originally provided by Termux; here they are
**implemented from scratch** by this project (source in [`tools/jrelibs/`](tools/jrelibs/)):

| Library | Purpose | Implementation |
|---|---|---|
| `libandroid-shmem` | JVM's SysV shared memory (`libandroid_shmget / shmat / shmdt / shmctl`) | `memfd_create` + `mmap`, raw syscalls only |
| `libandroid-spawn` | JVM's child-process spawning (`posix_spawn`) | Forwards to bionic; on Android 8.x (where bionic lacks `posix_spawn`) falls back to a built-in `clone + execve` |

Both are cross-compiled with [Zig](https://ziglang.org/) (`zig cc -target aarch64-linux-android`); see the header comments in the sources for the exact command.

**③ As a result, the only non-OpenJDK native libraries left in the JRE are:**

- `libz.so.1` — standard zlib (zlib license)
- `libandroid-shmem.so` / `libandroid-spawn.so` — self-implemented by this project

> **Why no JRE 8?** There is no clean, standalone JRE 8 distribution readily available for Android (the Termux
> repository has no Java 8; PojavLauncher's public releases are iOS builds, and its Android builds live only in
> login-gated CI artifacts). Only MC 1.16 and older need Java 8, while modern Paper / Purpur / Folia all require
> 1.17+, so JRE 8 has not been bundled since 1.0.13.

**Path inside the APK**: `assets/jre/jre{major}/universal.tar.xz` (a single archive is the complete JRE home).

On first use of a version, `JreManager` streams it out with `commons-compress + XZ` into `files/jre/jre{major}/`,
extracting first into a `cacheDir` staging directory and atomically swapping on success, so a half-extracted JRE is
never mistaken for a complete one.

Automatic matching by MC version (manually overridable in the wizard):

| MC version | JRE | Extracted size |
|---|---|---|
| 26.x and later (new numbering) | 25 | ~160 MB |
| 1.20.5 – 1.21.x | 21 | ~130 MB |
| 1.17 – 1.20.4 | 17 | ~100 MB |
| 1.16 and older | — | Unsupported (needs Java 8, no longer bundled) |

> Only the version required by the current instance is extracted (chosen automatically at creation time).

## Server support

- **Vanilla** (Mojang `launchermeta` manifest)
- **Paper / Folia** (`fill.papermc.io` v3)
- **Purpur** (`api.purpurmc.org` v2)

A mirror / proxy prefix can be configured under "Settings → Download sources & mirrors" (leave empty to use the official sources).

## Launch command

```
{JRE}/bin/java -server -Xms{m} -Xmx{m}
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200
  -Dfile.encoding=UTF-8 -Djava.io.tmpdir={instance/tmp}
  -jar {server.jar} nogui
```

Folia's usable core count is limited via `-XX:ActiveProcessorCount=N` (Folia derives its region thread count from it), avoiding saturation of all cores.

## Foreground service & keep-alive

When a server is running, a foreground service is started with `foregroundServiceType="dataSync|specialUse"` and
`stopWithTask=false`; **API 34+ uses `specialUse`** (to avoid Android 15/16's 6-hour limit on `dataSync`), older
versions use `dataSync`. The notification shows flavor / version / JRE / port / player count / memory. A
`PARTIAL_WAKE_LOCK` and a `WIFI_LOCK` are held; notification permission is requested after first install, and the
settings page guides the user to add the app to the battery-optimization whitelist.

## Building

Requires **JDK 21**, **Android SDK** (platform 34 + build-tools 34.0.0) and **Gradle 8.7**.

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
```

Alibaba Cloud mirrors are already configured in `settings.gradle.kts` to speed up dependency resolution.

**Signing**: supply your keystore via `local.properties` or environment variables. The bundled
`app/release.keystore` is for local demonstration only — **do not commit it or its password to a public repository**.

## Compliance & licensing

- This project is licensed under the **GNU AGPL v3** (see [`LICENSE`](LICENSE)).
- **No Mojang / Minecraft binaries or game assets are bundled**; this is only a downloader and launcher. All server jars are fetched from official or authorized sources at the user's request, and their use is subject to the Minecraft EULA.
- The bundled OpenJDK follows **GPLv2 + Classpath Exception** and is distributed with the APK as mere aggregation.
- This project contains **no source code or files from any third-party Minecraft launcher** (e.g. FoldCraftLauncher, PojavLauncher, Boardwalk).
- A list of third-party components is in [`NOTICE`](NOTICE).

## Changelog

### 1.0.0
- **Added "Skin Forge"**: reachable from the top-right of the home screen (left of the settings button). Deterministically generates a 64×64 Minecraft Java skin (dual-layer) from a "seed + style", with custom seeds, a random/reroll button, 8 styles (adventurer / hoodie / knight / mage / ranger / cyber / ninja / street) and "auto", live preview, and PNG export. The same seed + style always yields the same skin. The algorithm is constraint-based procedural generation (xorshift32 PRNG + hue/saturation/lightness guards + contrast checks), implemented purely in Kotlin.
- **Trimmed the bundled JRE**: removed 10 native libraries a server never uses (graphics / audio / debug); replaced `libandroid-shmem` and `libandroid-spawn` with **self-implemented** versions and dropped `libc++_shared`. The only non-OpenJDK native libraries left in the JRE are `libz.so.1` plus the two self-implemented ones.
