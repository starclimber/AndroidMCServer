# 更新日志
# Changelog

本项目的版本变更记录。 / Release notes for this project.

---

## [1.2.0] — 2026-10-03

### 界面 / Interface

- 状态栏改为**透明**并让内容延伸其后（edge-to-edge）；挖孔屏下标题不再被摄像头遮挡。
  The status bar is now **transparent** with content extending behind it (edge-to-edge); titles are no longer hidden by a punch-hole camera.
- 底部**手势导航条（小横条）**区域不再是一条黑带，内容自动避让。
  The bottom **gesture bar** area is no longer a black strip; content avoids it automatically.
- 关闭系统栏的自动对比度底衬。
  Disabled the system's automatic system-bar contrast scrim.

### 页面结构 / Page structure

- 「文件」与「配置」拆分为**两个独立页面**，实例卡片与控制台底部各有入口（原先是同一页面的多个 Tab）。
  Split **Files** and **Config** into two separate pages, reachable from both the instance card and the console toolbar (previously tabs of one page).
- 「插件」页整合为 **4 个 Tab**：本地导入 / 链接下载 / 在线搜索 / 已安装。
  Consolidated **Plugins** into **4 tabs**: local import / download from link / online search / installed.
- 诊断相关入口精简为 **「诊断」+「错误摘要」**（JRE 自检与深度探测收进诊断对话框）。
  Reduced diagnostics to **Diagnose + Error summary** (JRE self-test and deep probe moved inside the diagnose dialog).

### 新增 / Added

- **导入插件**：从手机选择 `.jar`，或粘贴直链下载到 `plugins/`；可查看并删除已安装插件。
  **Import plugins**: pick a local `.jar`, or download from a direct link into `plugins/`; browse and delete installed plugins.
- 关于页新增项目仓库链接。
  Added a repository link on the About screen.

### 修复 / Fixed

- **下载服务端时的版本列表**（Paper / Folia）偶发空白：HTTP 自动重试（网络异常 / 429 / 5xx），失败时显示原因与重试按钮，空结果不再被缓存。
  The **version list shown when downloading a server** (Paper / Folia) sometimes came up empty: HTTP requests now auto-retry (network errors / 429 / 5xx), failures show a reason plus a Retry button, and empty results are no longer cached.
- **Android 14+ 灭屏后 WiFi 锁失效**导致服务端掉线（`WIFI_MODE_FULL_HIGH_PERF` 被系统自动替换为仅亮屏生效的 `WIFI_MODE_FULL_LOW_LATENCY`），改用 `WIFI_MODE_FULL`。
  **Wi-Fi lock stopped working with the screen off on Android 14+** (`WIFI_MODE_FULL_HIGH_PERF` is auto-replaced by `WIFI_MODE_FULL_LOW_LATENCY`, which only holds while the screen is on), now uses `WIFI_MODE_FULL`.
- 规避 Android 15+ 对前台服务的运行时长限制。
  Avoided Android 15+ foreground-service runtime limits.
- 退出控制台后偶发白屏。
  Occasional blank screen right after leaving the console.
- 英文界面下 `server.properties` 键名括号内重复英文。
  English UI no longer repeats the raw key in parentheses.

### 变更 / Changed

- 版本号 `1.1.0` → `1.2.0`（versionCode 3）。

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
