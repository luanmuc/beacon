# beacon

Android TV / 手机双端的聚合播放器，电视台直播与影视点播合并，一个入口切换。

- 基座：`FongMi/TV` 4 月版（`16d347a9`），在其上做二开
- 播放内核：自有 media3 fork（`luanmuc/media@release-1.11.0-fongmi` + 补丁）
- 源与订阅：`beacon-feeds` 定时探测维护，`beacon-seed` 提供固定订阅地址
- 构建：GitHub Actions 两阶段（先编 21 个 media3 模块，再编双端 release），每日定时出包并自动发布 Release

## 特性

| 能力 | 说明 |
|---|---|
| 双端一套代码 | `leanback`（TV）/ `mobile`（手机），一次构建出两套 APK |
| 直播 + 影视合并 | 首页底部导航“影视 / 直播”切换，直播入口常驻 |
| 默认订阅 | 首次启动自动写入 `beacon-seed` 的订阅地址，装完即用 |
| 源自动维护 | 探测工作流每 6 小时跑一次，产物自动同步到公开种子仓库 |
| 内容面板 | 播放页展示剧名、来源、集数、播放地址、请求头，带上一集/选集/下一集/复制 |
| 解码面板 | 显示当前解码方式，可切硬解 / 软解 |
| 字幕调节 | 字号、位置可调（随播放保存） |
| 自动版本号 | `versionCode` 取 CI 运行号，`versionName` 为 `1.0.<运行号>` |

## 操作提示

- 播放页内容面板：分享、复制地址、重试当前线路、上一集/选集/下一集
- 直播页长按“首页/刷新”按钮：手动切下一条线路
- 直播页长按分组栏：搜索频道，点“重置”恢复全部
- 播放页长按播放键：定时关闭
- 播放页长按倍速按钮：弹出 0.5x–3.0x 预设
- 设置页点直播订阅地址：自检直播订阅可访问性
- 设置页点订阅地址：连通性自检；长按直播订阅地址：查看探测存活统计；长按影视订阅地址：复制地址
- 设置页长按版本号：打开项目主页；点击版本号：检查更新

## 仓库

- 主程序：`luanmuc/beacon`（本仓库）
- 源探测：https://github.com/luanmuc/beacon-feeds
- 订阅种子：https://github.com/luanmuc/beacon-seed
- 骨架试验：https://github.com/luanmuc/beacon-sketch

## 固定订阅地址

- 影视：`https://raw.githubusercontent.com/luanmuc/beacon-seed/main/tvbox.json`
- 直播：`https://raw.githubusercontent.com/luanmuc/beacon-seed/main/live.m3u`

## 本地构建

```bash
# 需要 Android SDK、JDK 17、Python 3.10（Chaquopy）
./gradlew :app:assembleLeanbackArm64_v8aRelease
./gradlew :app:assembleMobileArm64_v8aRelease
```

release 构建依赖 `local.properties` 中的 `storeFile / keyAlias / storePassword / keyPassword`。CI 里由 `keytool` 现场生成调试用 keystore。

## 发布

每次构建成功后自动创建 Release `v1.0.<run_number>`，附件为四个 APK（leanback/mobile × arm64-v8a/armeabi-v7a）。

## 许可

上游为 GPL-3.0，本仓库作为二开同样遵循 GPL-3.0。仅个人自用，不对外分发。
