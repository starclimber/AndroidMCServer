# Tiny MC Server

A Minecraft Java Edition **server launcher** that runs on an Android phone.

It bundles a trimmed OpenJDK runtime so the device can create and run Vanilla, Paper, Purpur and Folia servers directly — no root, no Termux, and no client-side rendering.

**English** | [简体中文](README.md)

---

## Features

- **One-tap instance creation**: pick a server flavor and game version; the app matches the runtime and downloads the corresponding server jar automatically.
- **Console**: live logs, command input, readiness checks, crash diagnostics.
- **File management**: browse the server directory, edit `server.properties` (with localized labels) and text files, with binary-file detection.
- **Plugin manager**: search and install plugins from Modrinth, with download integrity checks, progress and cancel.
- **Backups & player management**: back up / restore instances, manage whitelist / OP / ban lists.
- **Skin Forge**: deterministically generate a 64×64 Minecraft Java skin from a "seed + style", exportable as PNG.
- **Background reliability**: foreground service + WakeLock + WifiLock + battery-optimization whitelist guidance.

## Requirements

| Item | Requirement |
|---|---|
| OS version | Android 8.0 (API 26) or newer |
| CPU ABI | `arm64-v8a` |
| Storage | Depends on the server and runtime; typically 200 MB or more |

## Installation

1. Download the latest `TinyMCserver-<version>-arm64.apk` from the project's Releases page.
2. Allow installation from unknown sources on the device, then install the APK.

## Getting started

1. Open the app and tap **+** at the bottom right to create an instance.
2. Enter a name, choose a server flavor (Vanilla / Paper / Purpur / Folia) and a game version, set memory, and accept the EULA.
3. Tap **Create & download server**: the app extracts the runtime and downloads the server jar automatically (with progress), then opens the console.
4. Tap **Start** in the console to run the server. If a download is interrupted, the "Readiness check" panel lets you retry the runtime or the server individually.

## Supported servers

| Flavor | Source |
|---|---|
| Vanilla | Mojang `launchermeta` manifest |
| Paper / Folia | `fill.papermc.io` v3 |
| Purpur | `api.purpurmc.org` v2 |

This app **does not bundle any server software**; all server jars are downloaded on demand from the sources above when an instance is created.

A mirror or proxy prefix can be configured under "Settings → Download sources & mirrors".

## Skin Forge

A built-in skin generator that produces 64×64 dual-layer skins for Minecraft Java Edition.

**Entry point**: top-right of the home screen (left of the settings button).

**Usage**:

1. Enter any **seed** (or tap "Reroll" to generate one at random);
2. Choose a **style** (or "Auto");
3. Preview the result live, and **export it as a PNG**.

The same "seed + style" always produces the same skin, making them easy to share and reproduce.

**Styles**: Adventurer / Hoodie / Knight / Mage / Ranger / Cyber / Ninja / Street, plus "Auto".

**Implementation**: constraint-based procedural generation — an xorshift32 PRNG drives the randomness, with hue-harmony relationships, saturation / lightness guards and lightness-contrast checks constraining the palette. Written purely in Kotlin, with no third-party code.

## Bundled runtime (JRE)

Three versions of the OpenJDK runtime (aarch64) are bundled and matched to game versions. They come from the official Termux repository and are distributed with the APK as mere aggregation; see [`NOTICE`](NOTICE) for copyright and origin.

| Game version | Runtime |
|---|---|
| 26.x and later | JRE 25 |
| 1.20.5 – 1.21.x | JRE 21 |
| 1.17 – 1.20.4 | JRE 17 |

Each runtime is stored as a single archive at `assets/jre/jre{17,21,25}/universal.tar.xz` and extracted into the app's private directory on first use (first into a staging directory, then atomically swapped in).

To reduce size and keep dependencies clean, the upstream runtime is trimmed and two of its native libraries are replaced with self-implemented versions:

- **`libandroid-shmem`** — provides SysV shared memory to the JVM (implemented on `memfd_create` + `mmap`).
- **`libandroid-spawn`** — provides `posix_spawn` to the JVM (uses the system implementation where available, falling back to a built-in one on older systems).

The sources of both libraries are in [`tools/jrelibs/`](tools/jrelibs/) (Apache-2.0) and are cross-compiled with Zig.

## Architecture

1. **UI layer** (Jetpack Compose): instance list, creation wizard, console, file editor, settings, player management, backup, plugins, Skin Forge.
2. **Server Manager layer**: instance model, version resolution, runtime matching, launch-argument assembly, lifecycle, log parsing, auto-restart.
3. **Runtime layer**: the bundled JRE, launching `java -jar <server.jar> nogui` via `ProcessBuilder` (headless).
4. **Native / OS layer**: foreground service, WakeLock / WifiLock, SAF export, network listeners, JRE native libraries.

## Data & storage

All data lives in the app's private directory; no storage permission is required:

```
/data/data/dev.tinymcserver.app/
  files/jre/jre{major}/     # extracted runtime
  files/instances/<id>/     # server.jar, eula.txt, server.properties, plugins/, world/, backups/
```

## Background & keep-alive

While a server runs, a foreground service is started (`foregroundServiceType="dataSync|specialUse"`, `stopWithTask=false`).
The notification shows the flavor, version, runtime, port, player count and memory usage; a `PARTIAL_WAKE_LOCK` and a `WIFI_LOCK` are held while running.
Adding the app to the battery-optimization whitelist is recommended for more stable background operation.

## Building

Requires **JDK 21**, **Android SDK** (platform 34 + build-tools 34.0.0) and **Gradle 8.7**.

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
```

**Signing**: create `local.properties` in the project root with the following keys:

```properties
RELEASE_STORE_FILE=release.keystore
RELEASE_STORE_PASSWORD=<password>
RELEASE_KEY_ALIAS=<alias>
RELEASE_KEY_PASSWORD=<password>
```

Both `local.properties` and the keystore file are listed in `.gitignore` and are never committed.

## License

This project is licensed under the **GNU Affero General Public License v3.0**; see [`LICENSE`](LICENSE).

Third-party component origins and copyrights are listed in [`NOTICE`](NOTICE).
