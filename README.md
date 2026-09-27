# Tiny MC Server

[English](README.en.md) | **简体中文**

纯手机 Minecraft **Java 版服务端**启动器。不做客户端渲染、不集成 Termux、不要求 root。

- 包名：`dev.tinymcserver.app`
- 目标架构：`arm64-v8a`（内置 JRE 为 aarch64）
- 系统要求：**Android 8.0（API 26）及以上**（`minSdk 26` / `targetSdk 28`，原因见下）
- 当前版本：`1.0.0`

> **为什么 `targetSdk` 是 28？**
> Android 10（API 29）起，`targetSdk >= 29` 的应用被禁止对**私有目录内的文件**调用 `exec()`
> （W^X 限制，见 Android 10 行为变更 *execute-permission*）。而内置 JRE 必须从私有目录执行 `java`，
> 因此沿用 Termux 的兼容方案：保持 `targetSdk = 28`（`compileSdk` 仍为 34）。
> 若将来必须上架 Google Play（要求 `targetSdk >= 34`），需改用「把 JRE 可执行文件放进 `jniLibs`、
> 从 `nativeLibraryDir` 执行」的方案。

## 功能

- 一键创建实例：选类型（Vanilla / Paper / Purpur / Folia）、选 MC 版本，自动匹配并解压内置 JRE、自动下载对应服务端 jar；
- 控制台：实时日志、发送命令、就绪检查、崩溃诊断；
- 文件编辑：`server.properties` 中文标签、只读浏览与文本编辑、二进制识别；
- 插件管理：从 Modrinth 搜索并一键安装（带完整性校验、进度与取消）；
- 备份 / 还原、玩家管理（白名单 / OP / 封禁）；
- 真机保活：前台服务 + WakeLock + WifiLock + 电池优化白名单引导；
- 附带「**皮肤工坊**」：按「种子 + 风格」确定性生成 64×64 的 Minecraft Java 版皮肤，可导出 PNG。

## 使用流程

1. 打开 App → 右下角 **+** 新建。
2. 填名称、选类型（Vanilla / Paper / Purpur / Folia）、选 MC 版本（自动匹配 JRE）、调内存 → 勾选同意 EULA。
3. 点 **「创建并下载服务端」**：App 会**自动**解压匹配的 JRE + 从官方源下载服务端 jar（带进度弹窗），完成后进入控制台。
4. 控制台点 **启动** 即可。若下载中断，控制台顶部有「就绪检查」卡片可单独重试 JRE / 服务端，或点「一键准备」。

## 文件都在哪（全部在 App 私有目录）

```
/data/data/dev.tinymcserver.app/
  files/jre/jre{major}/        ← 内置 JRE 解压后的家目录（bin/java、lib/modules、libjvm.so…）
  files/instances/<实例id>/     ← server.jar、eula.txt、server.properties、plugins/、world/、backups/
```

不需要任何存储权限，不写共享存储。

> JRE 为什么要「解压」：Android **不能执行 APK 内部的二进制**，`java` 必须是磁盘上的真实可执行文件，
> 所以要把 APK assets 里的 JRE 解压到私有目录再运行。

## 技术分层

1. **Android UI 层**（Jetpack Compose）：实例列表、创建向导、控制台、文件编辑器、设置、玩家管理、备份、插件、皮肤工坊。
2. **Server Manager 层**：实例模型、版本解析、JRE 匹配、启动参数组装、生命周期、日志解析、自动重启。
3. **Runtime 层**：内置 JRE，通过 `ProcessBuilder` 启动 `java -jar xxx.jar nogui`，纯 headless。
4. **Native/OS 层**：前台服务、WakeLock/WifiLock、SAF 导出、网络监听、JRE `.so` 依赖。

## 内置 JRE

内置三个版本的 **Android bionic 版 OpenJDK**（aarch64），按 MC 版本匹配使用。
许可证为 **GPLv2 + Classpath Exception**，版权与来源声明见仓库根目录 [`NOTICE`](NOTICE)。
其完整许可证原件随二进制分发，位于每个归档内的 `legal/` 目录。

**来源**：**Termux 官方仓库**（`packages.termux.dev`）的 `openjdk-17 / 21 / 25`（aarch64）二进制包。
本项目在此基础上做了**大幅裁剪与自研替换**，以减小体积、去除用不到的模块并让依赖更干净：

**① 裁剪** —— 删除与运行无关的内容：`jmods/`、`demo/`、`man/`、`include/`、`ct.sym`，以及 `bin/` 下除 `java` 外的工具；
并删除服务端用不到的原生库：

- 图像 / 声音：`libjpeg`、`liblcms2`、`libasound`、`libandroid-sysv-semaphore`、`libjavajpeg`、`liblcms`、`libjsound`
- 调试 / Agent：`libinstrument`（`-javaagent`）、`libjdwp`（远程调试）、`libiconv`

**② 自研替换** —— 以下两个库原本由 Termux 提供，本项目**自行实现**（源码见 [`tools/jrelibs/`](tools/jrelibs/)）：

| 库 | 用途 | 实现方式 |
|---|---|---|
| `libandroid-shmem` | JVM 的 SysV 共享内存（`libandroid_shmget / shmat / shmdt / shmctl`） | `memfd_create` + `mmap`，纯系统调用 |
| `libandroid-spawn` | JVM 的子进程启动（`posix_spawn`） | 优先转发到 bionic；Android 8.x（bionic 尚无 `posix_spawn`）时回退到自带的 `clone + execve` |

两个库均用 [Zig](https://ziglang.org/) 交叉编译（`zig cc -target aarch64-linux-android`），构建命令见源码头部注释。

**③ 最终，JRE 内非 OpenJDK 来源的原生库只剩：**

- `libz.so.1` —— 标准 zlib（zlib license）
- `libandroid-shmem.so` / `libandroid-spawn.so` —— 本项目自研

> **为什么没有 JRE 8？** Android 平台没有可直接获取的独立 JRE 8 发行版（Termux 官方仓库不含 Java 8；
> PojavLauncher 的公开 Release 是 iOS 版，其 Android 版只存在于需登录的 CI 产物中）。而只有
> MC 1.16 及更早需要 Java 8，现代 Paper / Purpur / Folia 都在 1.17+，故自 1.0.13 起不再内置。

**APK 内路径**：`assets/jre/jre{major}/universal.tar.xz`（单个归档即完整 JRE Home）。

首次使用某版本时，`JreManager` 用 `commons-compress + XZ` 流式解压到 `files/jre/jre{major}/`，
先解压到 `cacheDir` 暂存目录、成功后再原子替换，避免半成品被误判为已安装。

按 MC 版本自动匹配（可在向导中手动覆盖）：

| MC 版本 | JRE | 解压后体积 |
|---|---|---|
| 26.x 及以后（新编号） | 25 | ~160MB |
| 1.20.5 – 1.21.x | 21 | ~130MB |
| 1.17 – 1.20.4 | 17 | ~100MB |
| 1.16 及更早 | — | 不支持（需要 Java 8，本版本不再内置） |

> 只解压当前实例需要的那一个版本（创建时自动选择）。

## 服务端支持

- **Vanilla**（Mojang `launchermeta` manifest）
- **Paper / Folia**（`fill.papermc.io` v3）
- **Purpur**（`api.purpurmc.org` v2）

下载源可在「设置 → 下载源与镜像」配置镜像 / 代理前缀（留空使用官方源）。

## 启动命令

```
{JRE}/bin/java -server -Xms{m} -Xmx{m}
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200
  -Dfile.encoding=UTF-8 -Djava.io.tmpdir={instance/tmp}
  -jar {server.jar} nogui
```

Folia 通过 `-XX:ActiveProcessorCount=N` 限制可用核数（Folia 依此确定区域线程数），避免全核拉满。

## 前台服务与保活

服务端运行即启动前台服务，`foregroundServiceType="dataSync|specialUse"`，`stopWithTask=false`；
**API 34+ 传 `specialUse`**（规避 Android 15/16 对 `dataSync` 的 6 小时时长限制），低版本传 `dataSync`。
通知栏显示类型 / 版本 / JRE / 端口 / 玩家数 / 内存；持有 `PARTIAL_WAKE_LOCK` 与 `WIFI_LOCK`；
首次安装后申请通知权限；设置页引导加入电池优化白名单。

## 构建

需要 **JDK 21**、**Android SDK**（platform 34 + build-tools 34.0.0）、**Gradle 8.7**。

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease
```

依赖仓库已在 `settings.gradle.kts` 配置阿里云镜像加速。

**签名**：请通过 `local.properties` 或环境变量提供密钥信息（`app/release.keystore` 仅用于本地演示，
**不要把它和口令提交到公开仓库**）。

## 合规与许可

- 本项目采用 **GNU AGPL v3**（见 [`LICENSE`](LICENSE)）。
- **不内置任何 Mojang / Minecraft 二进制或游戏资源**，仅作为下载与启动器；所有服务端 jar 均在用户操作时从官方或授权源获取，使用须遵守 Minecraft EULA。
- 内置 OpenJDK 遵循 **GPLv2 + Classpath Exception**，以「聚合分发」方式随 APK 分发。
- 本项目**不含任何第三方 Minecraft 启动器**（如 FoldCraftLauncher、PojavLauncher、Boardwalk）的源代码或文件。
- 第三方组件清单见 [`NOTICE`](NOTICE)。

## 更新日志

### 1.0.0
- **新增「皮肤工坊」**：首页右上角（设置按钮左侧）进入。按「种子 + 风格」确定性生成 64×64 的
  Minecraft Java 版皮肤（双层），支持自定义种子、随机换一个、8 种风格（旅人 / 兜帽 / 铁卫 / 秘术 /
  游侠 / 义体 / 影刃 / 街头）与「自动」，实时预览，可导出 PNG。同一种子 + 同一风格必得同一张皮肤。
  算法为约束式程序化生成（xorshift32 随机源 + 色相 / 饱和度 / 明度护栏 + 明度对比检查），纯 Kotlin 实现。
- **精简内置 JRE**：删除服务端用不到的图像 / 声音 / 调试原生库共 10 个；`libandroid-shmem` 与
  `libandroid-spawn` 改为**自研实现**，并移除 `libc++_shared`。最终 JRE 内非 OpenJDK 来源的原生库仅剩
  `libz.so.1` 与两个自研库。
