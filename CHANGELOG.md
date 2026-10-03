# 更新日志
# Changelog

本项目的版本变更记录。 / Release notes for this project.

---

## [1.2.1] — 2026-10-03

### 新增 / Added

- **日志分享脱敏**（设置 → 通用，**默认开启**）：复制日志（诊断 / 错误摘要 / JRE 自检 / 深度探测）时自动处理隐私信息 ——
  玩家 **IP** → `x.x.x.x`；**UUID** 只保留前 8 位与后 4 位，中段以 `*` 覆盖；**玩家名** → `Player1` / `Player2`…（`/op`、`/ban` 等命令里的名字一并处理）。
  复制出来的内容可以直接贴进公开的 issue，不必手动删。可在设置中关闭。
  **Log-sharing sanitization** (Settings → General, **on by default**): when copying a log (diagnose / error summary / JRE self-test / deep probe), sensitive data is handled automatically —
  player **IPs** → `x.x.x.x`; **UUIDs** keep only the first 8 and last 4 characters with the middle masked by `*`; **player names** → `Player1` / `Player2`… (names inside `/op`, `/ban`, etc. are handled too).
  Whatever you copy can be pasted straight into a public issue — no manual redaction. It can be turned off in Settings.

### 变更 / Changed

- 版本号 `1.2.0` → `1.2.1`（versionCode 4）。
- 更新日志的「修复」条目改为以**症状**描述（例如「灭屏后掉线（Android 14+）」而不是「WiFi 锁失效」），方便按现象检索。
  Changelog "Fixed" entries are now phrased as **symptoms** (e.g. "drops off after screen-off (Android 14+)" instead of "Wi-Fi lock broken"), matching how people search.

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

> 条目按**用户看到的症状**撰写，便于按现象搜索，也方便直接对照 issue 模板里的「复现步骤」。

- **手机灭屏几分钟后，服务器掉线 —— 同一局域网也连不上**（Android 14 及以上）。
  **A few minutes after the phone's screen turns off, the server drops off — unreachable even on the same LAN** (Android 14+).
- **放在后台跑约 6 小时后，服务器被系统停掉**（Android 15 及以上）。
  **After roughly 6 hours in the background, the server is stopped by the system** (Android 15+).
- **从控制台返回后偶发白屏**。
  **Occasional blank screen right after returning from the console.**
- **「下载服务端」的版本列表偶发空白**（Paper / Folia）：现已自动重试，失败会说明原因并可再试。
  **The version list under "Download server" sometimes came up empty** (Paper / Folia): it now retries automatically, and on failure shows the reason with a Retry button.
- **英文界面下 `server.properties` 标签的括号里又重复了一遍英文**。
  **In the English UI the raw key was repeated inside the parentheses of `server.properties` labels.**

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
