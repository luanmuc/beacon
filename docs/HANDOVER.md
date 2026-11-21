# 项目交接说明（HANDOVER）

本文件面向“接手继续开发的人/另一个 AI 账号”，说明本项目由哪些仓库组成、彼此如何关联、哪些东西不在代码里、以及从零接手需要做哪些事。

## 1. 项目是什么

基于 `FongMi/TV` 四月版基座（提交 `16d347a9359781d2fefee638e3856e6cffddab53`，GPL-3.0）二次开发的 Android TV / 手机双端播放器：

- 一个 App 同时包含电视台直播与影视点播，首页一个入口切换；
- 影视源、直播源不写进 App，由外部源仓库定时探测、发布，App 只保存两个默认订阅地址；
- 应用显示名保持 `TV`，`applicationId` 保持 `com.fongmi.android.tv`；
- 只发布仓库链接和版本号，不把 APK 提交进仓库。

## 2. 三个仓库与关联

项目已收敛为三个自有仓库，不要再新增第四个：

| 仓库 | 作用 | 与其它仓库的关系 |
|---|---|---|
| `luanmuc/beacon` | App 壳。双端 UI、播放、收藏/历史、设置、诊断 | 编译时 checkout `luanmuc/media` 产出 aar；运行时读取 `beacon-feeds` 的 `release` 分支 |
| `luanmuc/beacon-feeds` | 源仓库。抓取公开 TVBox 配置与 iptv-org 清单，跑探测脚本，生成 `tvbox.json` / `live/all.m3u` / `manifest.json` / `live-report.json`，并维护 `release` 分支 | 被 App 默认订阅直接读取；所有产物提交在本仓 |
| `luanmuc/media` | media3 fork（分支 `release-1.11.0-fongmi`），4 处补丁 | 被 `beacon` 的 CI 以 `luanmuc/media@release-1.11.0-fongmi` checkout 并编 aar |

数据流：

```
公开源（TVBox 配置 / iptv-org）
        │  抓取 + 探测
        ▼
beacon-feeds（脚本 + GitHub Actions，每 6 小时 / 手动）
        │  commit 到 main，再把 release 分支移到同一提交
        ▼
beacon-feeds@release（tvbox.json / live/all.m3u / manifest.json / live-report.json）
        │
        ▼
beacon App（Config.java 里两个 raw.githubusercontent.com 默认订阅）
        ▲
        │  CI checkout + 编 aar
luanmuc/media（media3 fork）
```

旧的 `beacon-seed` 已取消，`beacon-sketch` 已删除；不要再恢复。

## 3. 代码里写死的关联点

接手时如果换 GitHub 账号/组织，下面这些位置必须一起改，否则会出现“代码能编、源拉不到、更新检查失效”：

- `app/src/main/java/com/fongmi/android/tv/bean/Config.java`
  - `SEED_VOD`、`SEED_LIVE`：默认订阅地址（当前指向 `beacon-feeds/release`）
- `app/src/main/java/com/fongmi/android/tv/utils/Github.java`
  - `URL`：检查更新 / 发布页地址（`luanmuc/beacon/releases/latest/download`）
- `.github/workflows/build-apk.yml`
  - media job 的 `repository: luanmuc/media`、`ref: release-1.11.0-fongmi`
- 设置页“检查源清单”、启动源更新提示里读取的 `beacon-feeds/release/manifest.json`
- `beacon-feeds` 两个 workflow 直接提交本仓并移动 `release` 分支，不再需要 `TV_SEED_TOKEN`

## 4. 构建链（只能在 CI 编）

本机环境没有 Android SDK，历史尝试也证明不能本地全量编译。CI 口径在 `beacon/.github/workflows/build-apk.yml`：

1. `media` job：checkout `luanmuc/media`，编出 4 个 aar（media3 相关），上传 artifact；
2. `app` job：下载 aar 放入 `libraries/<module>/`，`actions/setup-python@v5`（3.10，Chaquopy 需要）；
3. Bump version：`versionCode = run_number + 9000`、`versionName = 1.0.<run_number>`；
4. 四维任务 `assemble<Flavor><Abi>Release`（leanback/mobile × arm64/armv7）；
5. 产物重命名 `beacon-<run>-<flavor>-<abi>.apk`，建 tag `v1.0.<run>` 并发布 Release；
6. artifact 保留 1 天，避免 Actions 存储配额打满。

### 必须知道的编译坑

- 自定义 media3 aar 产物目录是 `libraries/<module>/buildout/outputs/aar/`，不是 `build/`；
- 4 月基座 `.gitignore` 忽略 `.github`，改 workflow 要 `git add -f`；
- 只改 `README`/`docs` 不会触发构建（workflow 有 `paths` 过滤），要带 `app/src/**` 等代码改动；
- Java 批量插入方法体时不要用“方法名是否已存在”做前置判断，容易漏插方法体（历史 `showSiteList`、`checkManifest` 都因此编译失败）；
- TV 端 `mBinding.widget.error` 是 `LinearLayoutCompat`，错误文本要取 `mBinding.widget.text`；
- `LiveConfig.get()` 返回 `LiveConfig`，取分组用 `getHome().getGroups()`；
- `Keep` 在 `bean` 包；`Prefers.getLong` 用两参带默认值；一个 AlertDialog 只能有一个 `setNeutralButton`。

## 5. 源生产线

`beacon-feeds`：

- `scripts/`：抓取、探测、生成 manifest；
- `.github/workflows/probe-live.yml`：cron `0 */6 * * *`，探测直播源，发布 `live/all.m3u` + `live-report.json` + `manifest.json`，提交到 `main` 后把 `release` 分支移到同一提交；
- `.github/workflows/probe-vod.yml`：探测影视站点，发布 `tvbox.json` + manifest，同样移动 `release`；
- 不再需要 `TV_SEED_TOKEN`，也不再向第二个仓库写产物。

`beacon-feeds@release` 结构：

- `tvbox.json`：当前 5 个 type=1 影视站点；
- `live/all.m3u`：38 个频道 / 38 条线路；
- `manifest.json`：`version`（UTC 日期）、`updated_at`、`vod_sites`、`live_channels`、`live_lines`、`fastest`；
- `live-report.json`：探测明细；
- `release` 分支：App 默认读这个分支，探测异常时不会立刻影响用户。

## 6. 版本与发布

- 每次 push 到 `april-base` 触发一次 CI，`run_number` 就是版本后缀，例如 `v1.0.132`；
- 发布页 `https://github.com/luanmuc/beacon/releases`；
- 当前策略是“大包提交”：一个提交塞多个互不冲突功能 → 一次构建 → 失败集中修，不再一个功能一个版本；
- 不把 APK 放进仓库，也不在对话里发 APK 文件，只发仓库链接 + 版本号。

## 7. 另一个账号接手需要做什么

1. **Fork 三个仓库**（`beacon`、`beacon-feeds`、`media`），全部设为 public；
2. 改第 3 节列出的写死地址：`Config.java`、`Github.java`、workflow 里的 `luanmuc/media`；
3. 在 `beacon-feeds` 上先手动生成/提交 `manifest.json`，并创建 `release` 分支；
4. push 到 `beacon` 的 `april-base`（或自己默认分支，同时改 workflow `on.push.branches`），等 CI 出 4 个 APK；
5. 装其中 `leanback-arm64` 或 `mobile-arm64` 验证：首页有“影视 N 站点 · 直播 M 源”、直播能播、设置里“检查源清单”能读到版本。

### 不能直接接手的东西

- GitHub secrets（签名相关）不会随公开仓库复制；
- 发布历史、Actions 缓存、artifact 不会复制；
- 固定签名 keystore 目前没有纳入仓库（CI 每次 `keytool` 现场生成），所以不同构建之间覆盖安装可能冲突；
- 本工作区的笔记、历史尝试记录（`projects/app/notes/`、`scratch/`）不在主仓库里。

## 8. 已知边界

- 5.8.8 全量合并、`nov-ui` 混合拆分均已判定做不通，不要再走；
- 电视源最终形态是“单个 QuickJS 源 + App 内源健康管理”，不是常驻服务器；
- 四月版基座只有 Exo 系播放内核，公开渠道取不到 `mpvplayer`/`iso`/`libass` 源码，内核切换短期不做；
- 豆瓣榜单、相似推荐明确不做；
- 影视源为公开配置聚合，站点合法性与长期可用性未评估；
- 真机验证仍不充分，自动软解重试、4xx 换站等逻辑需要在设备上复测。

## 9. 许可

基座与衍生代码遵循 GPL-3.0；二次开发部分同样按 GPL-3.0 发布。应用内“关于”保留基座与许可说明。
