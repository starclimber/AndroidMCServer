# Tiny MC Server

一个运行在 Android 手机上的 Minecraft Java 版**服务端启动器**。

它内置精简后的 OpenJDK 运行时，可让设备直接创建并运行 Vanilla、Paper、Purpur、Folia 服务端 —— 不需要 root，不依赖 Termux，也不涉及任何客户端渲染。

[English](README.en.md) | **简体中文**

---

## 特性

- **一键创建实例**：选择服务端类型与游戏版本，自动匹配运行时、自动下载对应服务端 jar。
- **控制台**：实时日志、发送命令、就绪检查、崩溃诊断。
- **文件管理**：浏览服务端目录，编辑 `server.properties`（带中文标签）与文本文件，识别二进制文件。
- **插件管理**：从 Modrinth 搜索并安装插件，下载带完整性校验、进度与取消。
- **备份与玩家管理**：实例备份 / 还原，白名单 / OP / 封禁管理。
- **皮肤工坊**：以「种子 + 风格」确定性生成 64×64 的 Minecraft Java 版皮肤，可导出 PNG。
- **后台保活**：前台服务 + WakeLock + WifiLock + 电池优化白名单引导。

## 系统要求

| 项 | 要求 |
|---|---|
| 系统版本 | Android 8.0（API 26）及以上 |
| CPU 架构 | `arm64-v8a` |
| 存储 | 取决于服务端与 JRE，通常 200 MB 以上 |

## 安装

1. 从项目 Releases 页面下载最新的 `TinyMCserver-<版本>-arm64.apk`。
2. 在设备上允许安装来自未知来源的应用，然后安装 APK。

## 快速开始

1. 打开应用，点击右下角 **+** 新建实例。
2. 填写名称，选择服务端类型（Vanilla / Paper / Purpur / Folia）与游戏版本，设置内存，并同意 EULA。
3. 点击 **创建并下载服务端**：应用会自动解压运行时并下载服务端 jar（带进度提示），完成后进入控制台。
4. 在控制台点击 **启动** 即可运行。若下载中断，可在控制台的「就绪检查」中单独重试运行时或服务端。

## 支持的服务端

| 类型 | 来源 |
|---|---|
| Vanilla | Mojang `launchermeta` manifest |
| Paper / Folia | `fill.papermc.io` v3 |
| Purpur | `api.purpurmc.org` v2 |

本应用**不内置任何服务端程序**，所有服务端 jar 均在创建实例时按需从上述来源下载。

可在「设置 → 下载源与镜像」中配置镜像或代理前缀。

## 皮肤工坊

内置的皮肤生成器，可为 Minecraft Java 版生成 64×64 的双层皮肤。

**入口**：首页右上角（设置按钮左侧）。

**用法**：

1. 输入任意**种子**（或点击「换一个」随机生成）；
2. 选择**风格**（或选「自动」）；
3. 实时预览生成结果，并可**导出为 PNG**。

相同「种子 + 风格」始终生成同一张皮肤，便于分享与复现。

**风格**：旅人 / 兜帽 / 铁卫 / 秘术 / 游侠 / 义体 / 影刃 / 街头，以及「自动」。

**实现**：约束式程序化生成 —— 以 xorshift32 作为随机源，通过色相和谐关系、饱和度 / 明度护栏与明度对比检查来约束配色。纯 Kotlin 实现，不依赖第三方代码。

## 内置运行时（JRE）

应用内置三个版本的 OpenJDK 运行时（aarch64，供不同游戏版本匹配使用），来源为 Termux 官方仓库。运行时随 APK 以「聚合分发」方式分发，版权与来源见 [`NOTICE`](NOTICE)。

| 游戏版本 | 运行时 |
|---|---|
| 26.x 及以后 | JRE 25 |
| 1.20.5 – 1.21.x | JRE 21 |
| 1.17 – 1.20.4 | JRE 17 |

运行时以单个归档形式存放于 `assets/jre/jre{17,21,25}/universal.tar.xz`，首次使用某版本时解压到应用私有目录（先解压到暂存目录，成功后再原子替换）。

为减小体积并保持依赖简洁，本项目对上游运行时做了裁剪，并以自研实现替换了其中两个原生库：

- **`libandroid-shmem`** —— 为 JVM 提供 SysV 共享内存（`memfd_create` + `mmap` 实现）。
- **`libandroid-spawn`** —— 为 JVM 提供 `posix_spawn`（优先使用系统实现，旧系统回退到自带实现）。

两个库的源码位于 [`tools/jrelibs/`](tools/jrelibs/)（GNU Affero General Public License v3.0），使用 Zig 交叉编译。

## 架构

1. **UI 层**（Jetpack Compose）：实例列表、创建向导、控制台、文件编辑器、设置、玩家管理、备份、插件、皮肤工坊。
2. **Server Manager 层**：实例模型、版本解析、运行时匹配、启动参数组装、生命周期、日志解析、自动重启。
3. **Runtime 层**：内置 JRE，通过 `ProcessBuilder` 启动 `java -jar <server.jar> nogui`（headless）。
4. **Native / OS 层**：前台服务、WakeLock / WifiLock、SAF 导出、网络监听、JRE 原生库。

## 数据与存储

所有数据都保存在应用私有目录，无需任何存储权限：

```
/data/data/dev.tinymcserver.app/
  files/jre/jre{major}/     # 解压后的运行时
  files/instances/<id>/     # server.jar、eula.txt、server.properties、plugins/、world/、backups/
```

## 后台与保活

服务端运行时启动前台服务（`foregroundServiceType="dataSync|specialUse"`，`stopWithTask=false`）。
通知栏显示服务端类型、版本、运行时、端口、玩家数与内存占用；运行期间持有 `PARTIAL_WAKE_LOCK` 与 `WIFI_LOCK`。
建议在系统设置中把本应用加入电池优化白名单，以获得更稳定的后台运行。

## 构建

需要 **JDK 21**、**Android SDK**（platform 34 + build-tools 34.0.0）与 **Gradle 8.7**。

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
```

**签名配置**：在项目根目录创建 `local.properties`，填入以下键：

```properties
RELEASE_STORE_FILE=release.keystore
RELEASE_STORE_PASSWORD=<口令>
RELEASE_KEY_ALIAS=<别名>
RELEASE_KEY_PASSWORD=<口令>
```

`local.properties` 与密钥文件均已包含在 `.gitignore` 中，不会进入版本控制。

## 许可

本项目采用 **GNU Affero General Public License v3.0**，全文见 [`LICENSE`](LICENSE)。

第三方组件的来源与版权信息见 [`NOTICE`](NOTICE)。
