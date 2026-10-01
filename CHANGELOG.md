# 更新日志
# Changelog

本项目的版本变更记录。 / Release notes for this project.

---

## [1.1.1] — 2026-10-01

### 变更 / Changed

- 项目更名为 **Android MC Server**（原 Tiny MC Server）。应用名称、关于页、通知标题与日志前缀同步更新。
  **Renamed** the project to **Android MC Server** (formerly Tiny MC Server). The app name, About screen, notification title and log prefixes were updated accordingly.
- 仓库地址同步变更为 <https://github.com/starclimber/AndroidMCServer>。
  The repository URL is now <https://github.com/starclimber/AndroidMCServer>.
- 包名（`dev.tinymcserver.app`）与签名密钥**保持不变**，可直接覆盖安装升级，无需卸载。
  The package name (`dev.tinymcserver.app`) and signing key are **unchanged**, so this version upgrades in place without uninstalling.
- 版本号 `1.1.0` → `1.1.1`（versionCode 3）。

---

## [1.1.0] — 2026-09-30

### 新增 / Added

- **多语言支持**：界面与 `server.properties` 标签现支持 **中文 / English / Français / Español / Русский**（共 5 种）。
  首次启动按系统语言自动选择并记住；可在「设置 → 语言」中随时切换，切换即时生效、不重建界面，无闪烁。
  **Multilingual support**: the UI and `server.properties` labels now support **Chinese / English / Français / Español / Русский** (5 languages).
  The language is auto-detected on first launch and remembered; it can be changed anytime in Settings, taking effect instantly without recreating the UI.
- **隐藏最近任务**：设置中新增开关（默认关闭），开启后本应用不出现在系统「最近任务」列表中。
  **Hide from recents**: a new setting (off by default) that removes the app from the system's recents list.
- 多语言覆盖**全部文案**，包括运行日志、崩溃诊断与自检输出。
  The translation covers **all strings**, including runtime logs, crash diagnostics and self-test output.

### 修复 / Fixed

- 首页空状态提示文案换行后未居中（现按行居中）。
  The home empty-state text was not centered when wrapped across lines (now centered per line).

### 变更 / Changed

- 版本号 `1.0.0` → `1.1.0`（versionCode 2）。

---

## [1.0.0] — 2026-09-28

### 首发 / Initial release

- 创建并运行 **Vanilla / Paper / Purpur / Folia** 服务端
  Create and run **Vanilla / Paper / Purpur / Folia** servers on-device.
- 控制台：实时日志、发送命令、就绪检查、崩溃诊断
  Console: live logs, command input, readiness checks, crash diagnostics.
- 文件浏览与 `server.properties` 标签化编辑
  File browser and labeled editing of `server.properties`.
- 从 Modrinth 搜索并安装插件（带完整性校验）
  Search and install plugins from Modrinth (with integrity checks).
- 实例备份 / 还原、玩家管理（白名单 / OP / 封禁）
  Instance backup / restore and player management (whitelist / OP / ban).
- 皮肤工坊：以「种子 + 风格」确定性生成 64×64 皮肤
  Skin Forge: deterministically generate 64×64 skins from a seed + style.
- 前台服务保活（WakeLock / WifiLock）
  Background reliability via a foreground service (WakeLock / WifiLock).
- 内置精简 **JRE 17 / 21 / 25**，含自研 `libandroid-shmem`、`libandroid-spawn`
  Bundled trimmed **JRE 17 / 21 / 25**, including the self-implemented `libandroid-shmem` and `libandroid-spawn`.
