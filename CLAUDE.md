# CLAUDE.md — maccms 手机端项目工作指南

本文件面向在本仓库中工作的 Claude 会话。开始任务前先读本文件；项目功能与使用说明见 [README.md](README.md)。

## 1. 项目定位

- **项目**：`maccms-mobile-tv`，maccms V10 站点的 Android 手机客户端。
- **来源**：由 `D:\projects\maccms-client-tv`（Android TV / Compose for TV 版）重构而来，功能与 TV 版一一对应，交互改为手机触控。
- **包名**：`com.maccms.mobile`。
- **技术栈**：Kotlin 2.2.10 + Jetpack Compose（Material 3）+ Media3 ExoPlayer 1.11.0 + Retrofit + Coil。
- **默认数据源**：`https://maccms.sanxininfo.cn`，接口 `api.php/provide/vod`，可在设置页修改。
- **远程仓库**（默认分支均为 `master`）：
  - `origin` → Gitee `git@gitee.com:shuizhuyu/maccms-android-apk.git`
  - `github` → GitHub `https://github.com/wtf-church/maccms-android-apk.git`
- **最低系统**：Android 6.0（minSdk 23）；compileSdk 36 / targetSdk 35。

## 2. 常用命令

### Docker 构建（推荐，环境与现有产物一致）

```bash
# 首次：构建带 Android SDK 36 的镜像
docker build -t maccms-mobile-build docker

# 构建 debug APK（约 5-7 分钟）
bash scripts-build.sh

# 产物
# app/build/outputs/apk/debug/app-debug.apk
# 仓库根目录 maccms-mobile-debug.apk 是随仓库发布的快照
```

### 本机 / Android Studio

需要 Android SDK Platform 36、Build-Tools 36.0.0：

```bash
./gradlew :app:assembleDebug
```

### 安装与启动（MuMu 模拟器）

```bash
ADB="D:/Program Files/Netease/MuMu/nx_main/adb.exe"
"$ADB" connect 127.0.0.1:5555
"$ADB" -s 127.0.0.1:5555 install -r maccms-mobile-debug.apk
"$ADB" -s 127.0.0.1:5555 shell am start -n com.maccms.mobile/.MainActivity
```

模拟器实例启动：`D:\Program Files\Netease\MuMu\nx_main\MuMuManager.exe control -v 0 launch`。

## 3. 测试环境注意事项

- MuMu 实例：Android 12 / x86_64 / 900×1600 / density 240。
- **横屏播放页用 `adb shell input tap` 注入坐标不可靠**（坐标仍按竖向空间解释），验证横屏控件时优先：
  1. 用系统返回键 `input keyevent 4`；
  2. 在竖屏页面验证同一组件（如详情页的选集弹层）；
  3. `uiautomator dump` 获取 bounds 后仅作参考。
- `adb shell input text` 不支持中文；中文搜索可点搜索页的「热门搜索」词条验证。
- 安装报签名不一致时先 `adb uninstall com.maccms.mobile`（仅在更换签名后需要；仓库已固定 debug keystore，正常情况直接 `install -r`）。

## 4. 架构速查

```
app/src/main/java/com/maccms/mobile/
├─ MainActivity.kt        # Scaffold + 底部导航 + NavHost + 横竖屏
├─ MaccmsApp.kt           # Application，Coil 图片缓存
├─ data/
│  ├─ ApiClient.kt        # Retrofit；Base URL 变化自动重建
│  ├─ VodRepository.kt    # 列表/搜索/详情；VodCache 跨页快照
│  ├─ Models.kt           # Vod/ApiResponse/VodParser（播放地址解析）
│  └─ Categories.kt       # 默认站点分类（ID + 名称）
├─ util/                  # BaseUrl / 收藏 / 历史 / 进度 / 搜索历史 / 富文本 / 格式化
└─ ui/
   ├─ theme/              # 深色观影主题
   ├─ components/         # PosterCard、ContinueWatchingCard、EpisodePicker、状态页
   ├─ home/ browse/ search/ library/ detail/ player/ settings/
```

导航路由：

- 一级（显示底部栏）：`home`、`browse`、`search`、`library`
- 二级（隐藏底部栏）：`settings`、`detail/{vodId}`、`player/{vodId}?episode=...`

状态提升（App 层持有，切 Tab/进详情不丢状态）：

- `HomeState`、`BrowseState`、`SearchState`，在 `MainActivity` 中 `remember` 后传入。

## 5. 关键实现与易错点

1. **列表滚动不要阻塞请求**
   - `LazyGridState.scrollToItem(0)` 在列表尚未组合时会**挂起**。不要在发起网络请求前顺序 `await` 它。
   - 现状：Browse/Search 把滚动放到独立协程，再发请求。曾因此导致浏览/搜索永久显示“加载中”。

2. **切页会取消 `rememberCoroutineScope`**
   - Home 的 `load()` 用 `try/finally` 复位 loading/refreshing。
   - Search 在重新进入页面时若 `loading == true` 自动重试。
   - 新增网络请求时请考虑协程取消后的状态复位。

3. **播放器**
   - 横屏 + 沉浸式 + 常亮：`PlayerScreen` 进入时设置 `requestedOrientation` 并隐藏系统栏，离开时恢复。
   - 音频焦点与耳机拔出：`setAudioAttributes(..., handleAudioFocus = true)`、`setHandleAudioBecomingNoisy(true)`。
   - 多地址回退：候选 URL 去重、m3u8 优先；失败自动尝试下一个。
   - HTML 分享页解析：`VodParser.m3u8FromHtml()`。
   - 进度保存：每 5 秒、暂停、切集、离开时写入 `PlaybackStore` / `WatchHistoryStore`；<5 秒或距结尾 10 秒视为未看/看完。
   - 自动连播：`STATE_ENDED` 且非换集加载中时播同线路下一集。

4. **分类为默认站点定制**
   - `Categories.ALL` / `HOME_IDS` 按 `maccms.sanxininfo.cn` 的 type_id 写死；换站点需调整。
   - 首页只请求 10 个精选分类，避免首屏过多请求；浏览页展示全部。

5. **存储键**
   - SharedPreferences 前缀 `maccms_mobile_*`，与 TV 版 `maccms_tv_*` 互不影响。
   - 收藏、历史、进度均只在本机。

6. **签名**
   - 固定调试签名：`keystore/debug.keystore`（标准 Android debug key，口令 `android`），保证多次 Docker 构建可覆盖安装。
   - 当前未配置 release 签名；正式分发前需创建 release keystore 并在 `app/build.gradle.kts` 增加 signingConfig。

7. **网络与请求头**
   - `usesCleartextTraffic=true`，支持局域网 HTTP 站点。
   - 播放器 UA + Referer 使用当前 Base URL，降低 CDN 防盗链失败率。

8. **UI 约定**
   - 全中文界面；深色 Netflix 风格（背景 `#0B0B0F`，品牌红 `#E50914`）。
   - 一级页面自行 `statusBarsPadding()`；底部栏由 Scaffold 处理导航栏 insets。
   - 海报统一 2:3；空 `vod_pic` 显示播放图标占位。

## 6. Git 与交付流程

- **开源协议**：MIT（[LICENSE](LICENSE)）；免责声明见 [DISCLAIMER.md](DISCLAIMER.md)。分发或修改时保留版权与许可声明，不要移除免责声明。
- 提交前检查 `git status`，以下内容不应入库：`.gradle/`、`.kotlin/`、`app/build/`、`local.properties`、IDE 文件、临时截图/日志。
- **根目录 `maccms-mobile-debug.apk` 是特意跟踪的构建快照**；重新构建后请同步复制：
  ```bash
  cp app/build/outputs/apk/debug/app-debug.apk maccms-mobile-debug.apk
  ```
- 推送：
  ```bash
  git push origin master   # Gitee
  git push github master   # GitHub
  git pushall              # 两个仓库一起推（Git 别名）
  ```
- 远程仓库名虽为 `*-apk`，但仓库同时包含完整源码、构建脚本、截图与 APK。
- 提交信息建议中文，结尾注明必要协作信息；不要提交密钥、token、服务器密码。

## 7. 与 TV 项目的对应关系

| TV 版 | 手机版 |
|---|---|
| `ui/home` 顶部导航 | 底部 Tab + `MainActivity` |
| 遥控焦点/缩放 | 触控点击/长列表 |
| 5 列网格 | 3 列网格 |
| `ui/player` 官方 Compose 控件 + D-pad | `PlayerView` + 自定义手势层 |
| 8×3 选集面板 | BottomSheet 5 列网格 |
| `maccms_tv_*` 存储 | `maccms_mobile_*` 存储 |

功能对齐是硬要求：新增 TV 端功能时，手机端应同步实现并适配触控交互。
