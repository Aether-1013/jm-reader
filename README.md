# JM Reader

一个原生 **Kotlin + Jetpack Compose** 安卓漫画阅读器。

> [!IMPORTANT]
> **免责声明** — 这是**非官方的第三方客户端**，仅供个人使用与技术学习。
> 本应用与 18comic / JMComic / 禁漫天堂 **无任何关联**，未获其认可或授权。
> 应用展示的漫画内容版权归原作者所有，由第三方源提供；**本应用自身不托管、不存储任何内容**。
> 请尊重版权，并遵守你所使用的内容来源的服务条款。

[English Readme →](README_EN.md)

## 功能

- 📖 **漫画浏览** — 首页（推荐 / 最新）、分类、每周必看、每日签到、热门标签、关键词搜索、随机推荐
- 📚 **原生阅读器** — 竖向 / 横向翻页、章节切换、进度条、自动**去打乱还原图片**
  （部分漫画页面以「水平条带倒序」方式分发，本应用会自动还原）
- 🌐 **三语界面** — 简体中文 / 繁體中文 / English，随时切换
- ⬇️ **一键下载** — 整本下载（全部章节、全部页面），去打乱后存为普通 JPEG 到
  `Download/JMReader/<专辑ID>/`：在下载目录可见、可经 USB 拷贝、任何图库都能打开，
  并支持内置**离线阅读**（无需网络）
- 🛡️ **崩溃日志** — 应用崩溃后，下次启动自动弹窗显示报错信息，可一键「复制日志」
- 👤 **会员功能** — 登录 / 注册、收藏、观看历史、每日签到、个人资料
- 🎬 **其它内容** — 小说、影片、游戏、部落格、论坛

**无广告** — 整个广告与充值 / 金币购买层均已移除。

## 构建

环境要求：JDK 17+，Android SDK（API 36）。

```bash
# 指向你的 Android SDK
echo "sdk.dir=C:\\path\\to\\Android\\Sdk" > local.properties

# Windows
gradlew.bat :app:assembleDebug

# macOS / Linux
./gradlew :app:assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`

Release 构建使用本地 `jmreader.keystore` 签名（见 `app/build.gradle.kts`）；
对外分发时请自行生成密钥库。

## 项目结构

```plaintext
app/src/main/kotlin/com/jm/reader/
├─ data/
│  ├─ net/         Crypto (md5/AES)、ApiClient (签名+解密)、HostManager (主机引导)
│  ├─ session/     SessionManager (jwt / 会员信息 / 主机缓存 / 语言)
│  ├─ model/       JSON 辅助 + 领域模型
│  ├─ repo/        AppRepository（类型化接口封装，不含广告/金币接口）
│  └─ download/    DownloadManager（下载到 Download/JMReader，离线索引）
├─ ui/
│  ├─ strings/     AppStrings（简中 / 繁中 / 英文）、UiLanguage、LanguageManager
│  └─ splash/ home/ detail/ reader/ download/ category/ search/
│     library/ member/ week/ daily/ novels/ movies/ games/ blogs/ forum/
└─ util/           ImageDescrambler、ReaderImageLoader (LRU 缓存)、CrashHandler
```

## 实现原理（概览）

- **主机发现** — 启动时从 CDN 拉取加密的服务器列表，解密后随机选择一个 API 主机
- **请求签名** — 每个请求携带由当前时间 + 静态密钥推导的 `Tokenparam` / `Token` 请求头
  （与参考 Web 客户端一致）
- **响应加密** — API 响应为 AES-256-ECB 加密，应用先解密再解析
- **图片去打乱** — 阅读器根据 `md5(专辑ID + 页码)` 计算确定的切片数，再重组倒序的水平条带
  （`util/ImageDescrambler.kt`）

## 协议

[GNU General Public License v3.0](LICENSE) — 详见 `LICENSE` 文件。

Copyright © 2026 — 欢迎贡献。
