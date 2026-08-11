# 语音笔记（Voice Notes）

一款"边录音边转笔记"的 Android 应用：**说话的同时实时显示转写文字，录音保存为音频文件，结束后自动生成一条可编辑、可分享的笔记**。

> 🌐 **[English Complete Tutorial (README_EN.md)](README_EN.md)** — 完整英文教程：安装、各识别引擎配置、自建后端、英文→中文注释、故障排查。

- 目标设备：Android 16（API 36），OPPO / ColorOS 16 亲测可用
- 最小支持：Android 8.0（API 26）
- 语言：Kotlin + 传统 View（无 Compose，构建简单稳定）
- 录音存储：应用内部存储（`filesDir/recordings/*.m4a`），笔记为 `filesDir/notes/*.json`

---

## English Introduction

**Voice Notes** is an Android app that **transcribes speech to text in real time while recording** — ideal for taking notes during meetings, lectures, or personal dictation.

### Key Features
- **Real-time transcription**: Words appear on screen as you speak (≈0.5–1s latency).
- **Continuous long recording**: Auto-renews sessions every 50 seconds (Xunfei engine) to support unlimited-length dictation.
- **Audio recording**: 16kHz AAC `.m4a` files (WAV fallback), stored privately in the app's internal storage.
- **Background recording**: Foreground service with a persistent notification — keeps working with the screen off.
- **Notes management**: Auto-titled notes with edit / playback / share / delete.
- **Multiple recognition engines**: Xunfei (default), Tencent Cloud, and Baidu AI — switch anytime in Settings; plus the on-device system recognizer when available.
- **Language switching**: Choose recognition language — Mandarin, Cantonese, English, or Sichuan dialect (support depends on the selected engine).
- **English→Chinese annotations**: When English is recognized, each sentence gets a Chinese translation underneath (via your own backend server or a third-party API such as DeepSeek).

### Requirements
- Android 8.0+ (API 26); optimized for Android 16 (API 36).
- One cloud ASR account (Xunfei / Tencent Cloud / Baidu AI) to obtain API keys — see the provider table below.

### Privacy
All recordings and notes are stored **on your device** in the app's private storage. Audio is sent to the selected cloud ASR provider **only while you are recording**, for the sole purpose of transcription.

---

## 功能

| 功能 | 说明 |
|---|---|
| 边录边转 | 说话时实时显示识别文字（讯飞流式接口，约 0.5~1 秒延迟） |
| 连续长录音 | 每 50 秒自动切换一段讯飞会话，实现无限时长连续转写 |
| 录音文件 | 16kHz AAC 编码 `.m4a`（文件小），AAC 不可用时自动回退 WAV |
| 后台录音 | 前台服务（`microphone` 类型）+ 常驻通知，锁屏/切后台仍可录 |
| 笔记列表 | 自动以转写前 20 字作为标题，可查看时长/日期/预览 |
| 编辑分享 | 可编辑标题与正文；分享文字、分享录音、删除 |
| 多引擎 | 讯飞 / 腾讯云 / 百度智能云 / **自建后端（Whisper，边录边出字）** / 系统识别（App 内切换） |
| 多语言 | 识别语言可切换：普通话 / 粤语 / 英语 / 四川话（支持程度取决于所选引擎） |
| 英文→中文注释 | 识别为英文时，自动给每句话加中文注释（自建后端 或 直连 DeepSeek 等 API） |

---

## 快速开始

### 1. 环境要求

- Android Studio **Narwhal（2025.1.1）或更新版本**（Ladybug 也可，但需要能识别 AGP 8.10）
- JDK 17（Android Studio 自带）
- 一台 Android 8.0+ 的手机（开发时打开 USB 调试）
- 首次 Sync 时请让 Android Studio 自动下载 **Android SDK Platform 36** 与依赖（需要联网）

### 2. 打开项目

1. 用 Android Studio 打开本项目目录 `d:\Projects\VoiceNotes`
2. 若提示 `Gradle wrapper not found`（缺少 `gradle/wrapper/gradle-wrapper.jar`），
   先在本目录运行一次：`powershell -ExecutionPolicy Bypass -File setup-gradle-wrapper.ps1`
   （脚本会从 GitHub/Gitee 下载 Gradle 8.11.1 的 wrapper jar；你的电脑需要能联网）
3. 等待 Gradle Sync 完成（首次会自动下载 Gradle 8.11.1 与依赖，需联网）

### 3. 注册并开通识别服务（选一家即可，均有免费额度）

| 服务商 | 注册/控制台网址 | 需要开通的服务 | 需要的密钥 | 说明 |
|---|---|---|---|---|
| **讯飞开放平台**（默认） | https://www.xfyun.cn | 语音听写（流式版） | AppID / APIKey / APISecret | 创建应用默认每日 500 次免费；国产机通用 |
| **腾讯云** | https://cloud.tencent.com/product/asr | 语音识别（实时语音识别 WebSocket） | AppID / SecretId / SecretKey | 首次有免费额度；16k 中文普通话 |
| **百度智能云** | https://cloud.baidu.com/product/speech | 实时语音识别 | AppID / API Key / Secret Key | 各接口有免费调用量；App 自动换取 token |
| **自建后端** | 自己电脑/服务器（见 `server/README.md`） | `/ws/transcribe` 流式（faster-whisper），失败自动回退 `/api/transcribe` | 后端地址 | 完全离线、自托管（思路参考 WhisperLiveKit）；**边录边出字** |
| 阿里云（预留） | https://www.aliyun.com/product/nls | 智能语音交互（NLS） | — | 后续可扩展 |
| 火山引擎（预留） | https://www.volcengine.com/product/voice | 语音识别 | — | 后续可扩展 |
| 系统识别 | 无需注册 | 设备自带 | — | 仅带 Google/系统识别服务的手机可用 |

**操作步骤（以讯飞为例，其他类似）：**
1. 打开上表对应网址，注册并登录（建议完成实名认证）
2. 进入控制台 → 创建应用 / 开通对应语音识别服务
3. 在控制台获取密钥（上表最后一列）
4. 安装 App 后，在应用内 **右上角设置** 中选择引擎并填入密钥，保存

> 注意：
> - 讯飞默认**不开启 IP 白名单**，手机 4G/5G/Wi-Fi 均可直接调用。
> - 腾讯云/百度若开通后提示权限或鉴权问题，确认已在新版控制台**开通对应付费/免费服务**。
> - 各家密钥请妥善保管，仅用于你自己的 App。

### 4. 英文→中文注释（可选）

识别到英文时，可为每句话自动添加中文注释。两种翻译来源（设置页切换）：

- **自建后端（推荐）**：在电脑上运行本项目 `server/` 目录的 FastAPI 服务（翻译可选 DeepSeek/OpenAI 兼容 API 或百度翻译，也可选装 faster-whisper 做自托管转写，思路参考 [WhisperLiveKit](https://github.com/QuentinFuxa/WhisperLiveKit)）。
  手机设置页填 `http://电脑IP:8000`（与电脑同一 Wi-Fi）。
  详见 [server/README.md](server/README.md)。
- **直连第三方 API**：在设置页填 DeepSeek（platform.deepseek.com）等 OpenAI 兼容平台的 API Key 即可，无需后端。

### 5. 构建并安装

方式一（Android Studio）：
- 手机开 USB 调试并连接电脑 → 点击 ▶ Run

方式二（命令行，需先补好 wrapper jar）：
```powershell
.\gradlew.bat assembleDebug
# 产物在 app\build\outputs\apk\debug\app-debug.apk
```

### 6. 使用

1. 首次使用点右下角 **⊕ 麦克风按钮** → 授予"麦克风""通知"权限
2. 进入录音页，开始说话，**转写文字实时出现**
3. 点 **停止并保存** → 回到列表即可看到笔记
4. 点开笔记：播放录音、编辑文字、分享为文本 / 分享录音文件

---

## 技术要点（对照 Android 16 官方文档）

| 需求 | 方案 |
|---|---|
| 实时语音转写（多引擎） | 统一接口 `IatStreamClient`（start/feedPcm/stop/shutdown），已内置三家：**讯飞**（`wss://iat-api.xfyun.cn/v2/iat`，HMAC-SHA256 + `dwa=wpgs` 字级实时）、**腾讯云**（`wss://asr.cloud.tencent.com/asr/v2/{appid}`，HMAC-SHA1 签名 + `slice_type` 结果）、**百度智能云**（`wss://vop.baidu.com/realtime_asr`，OAuth token + START/MID_TEXT/FIN_TEXT 帧）；另可选系统 `SpeechRecognizer` |
| 后台持续录音 | 前台服务 + `foregroundServiceType="microphone"`，声明 `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` 权限（Android 14+ 强制），启动前必须已授予 `RECORD_AUDIO` |
| 录音文件 | 单一 `AudioRecord` 采集 16kHz/16bit/单声道 PCM，一路喂当前引擎，一路经 `MediaCodec(AAC)+MediaMuxer` 写 `.m4a`（避免双开麦克风冲突） |
| Android 13+ 通知 | 运行时申请 `POST_NOTIFICATIONS`；前台服务通知带"停止"操作 |
| Android 16 edge-to-edge | 所有页面根布局通过 `WindowInsetsCompat.systemBars()` 应用内边距 |
| 主线程安全 | 识别回调经全局 `RecorderSession` 汇总，统一在主线程通知 UI |

### 代码结构

```
app/src/main/java/com/voicenotes/app/
├── MainActivity.kt            笔记列表
├── RecorderActivity.kt        录音转写页（实时文字/计时/音量）
├── NoteDetailActivity.kt      笔记详情（播放/编辑/分享/删除）
├── SettingsActivity.kt        设置（引擎选择 + 各家密钥）
├── model/Note.kt              笔记数据模型
├── data/NoteStore.kt          文件化笔记存储
├── data/Prefs.kt              SharedPreferences 设置
├── record/RecordingService.kt 前台录音服务（总调度，按引擎分发）
├── record/IatStreamClient.kt  流式识别客户端统一接口
├── record/XunfeiIatClient.kt  讯飞流式识别客户端
├── record/TencentIatClient.kt 腾讯云实时语音识别客户端
├── record/BaiduIatClient.kt   百度智能云实时语音识别客户端
├── record/SystemRecognizerClient.kt  系统识别（备用引擎）
├── record/AacFileWriter.kt    PCM→AAC(.m4a) 编码器
├── record/WavFileWriter.kt    PCM→WAV 兜底
├── record/RecorderSession.kt  服务↔UI 共享状态
└── ui/NotesAdapter.kt         列表适配器
```

---

## 常见问题

- **提示"请先到设置中填写讯飞密钥"**：去讯飞控制台拿 AppID/APIKey/APISecret 填进设置。
- **提示"连接讯飞失败"**：检查手机网络；讯飞控制台确认已开通"语音听写（流式版）"；确认密钥复制无空格。
- **国行 OPPO 无系统识别**：请使用默认的"讯飞云端识别"引擎；"系统识别"仅适合带 Google 服务的手机。
- **录音与识别不同步 / 前几秒没识别**：会话建立需要约 0.5~1 秒，属正常现象。
- **后台一段时间后被系统杀掉**：在系统设置里允许本应用后台运行/自启动（部分国产 ROM 默认限制）。

## 免责声明 / Disclaimer

**中文**
本应用（VoiceNotes）仅用于学习与个人使用，按"现状"提供，不提供任何明示或默示的担保。
- 云端语音识别产生的调用费用、免费额度与数据使用规则，以各服务商（讯飞 / 腾讯云 / 百度智能云）平台规则为准；
- 录音内容仅用于转写，请遵守当地法律法规，**未经他人同意请勿录音**；
- 请妥善保管各家 API 密钥，切勿泄露；如泄露请及时在对应控制台重置；
- 因使用本应用而产生的任何损失，作者不承担任何责任。

**English**
VoiceNotes is provided for learning and personal use only, "as is", without any express or implied warranty.
- Cloud speech recognition costs, quotas and data policies are subject to each provider's terms (Xunfei / Tencent Cloud / Baidu AI).
- Recorded audio is used only for transcription. Please comply with local laws and **do not record others without their consent**.
- Keep your API keys secure and reset them immediately if they are compromised.
- The author is not liable for any loss arising from the use of this application.
