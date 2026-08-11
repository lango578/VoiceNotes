# 语音笔记（Voice Notes）

一款"边录音边转笔记"的 Android 应用：**说话的同时实时显示转写文字，录音保存为音频文件，结束后自动生成一条可编辑、可分享的笔记**。

- 目标设备：Android 16（API 36），OPPO / ColorOS 16 亲测可用
- 最小支持：Android 8.0（API 26）
- 语言：Kotlin + 传统 View（无 Compose，构建简单稳定）
- 录音存储：应用内部存储（`filesDir/recordings/*.m4a`），笔记为 `filesDir/notes/*.json`

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
| 双引擎 | 默认讯飞云识别（国行 OPPO 可用）；可选系统识别（需设备带 Google 服务） |

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

### 3. 申请讯飞密钥（免费，10 分钟）

1. 打开 https://www.xfyun.cn 注册并登录
2. 进入**控制台 → 创建应用**（选 WebAPI 平台）
3. 在应用里**添加服务 → 语音听写（流式版）**（创建应用默认每天 500 次免费调用，个人笔记足够）
4. 在**控制台 → 服务页**复制三串密钥：
   - `AppID`、`APIKey`、`APISecret`（均为 32 位）
5. 安装 App 后，在应用内 **右上角设置** 中填入这三项，选择引擎为"讯飞云端识别"，保存

> 注意：讯飞服务默认**不开启 IP 白名单**，手机 4G/5G/Wi-Fi 均可直接调用，无需额外配置。

### 4. 构建并安装

方式一（Android Studio）：
- 手机开 USB 调试并连接电脑 → 点击 ▶ Run

方式二（命令行，需先补好 wrapper jar）：
```powershell
.\gradlew.bat assembleDebug
# 产物在 app\build\outputs\apk\debug\app-debug.apk
```

### 5. 使用

1. 首次使用点右下角 **⊕ 麦克风按钮** → 授予"麦克风""通知"权限
2. 进入录音页，开始说话，**转写文字实时出现**
3. 点 **停止并保存** → 回到列表即可看到笔记
4. 点开笔记：播放录音、编辑文字、分享为文本 / 分享录音文件

---

## 技术要点（对照 Android 16 官方文档）

| 需求 | 方案 |
|---|---|
| 实时语音转写 | `SpeechRecognizer` 之外的国产可稳定方案：**讯飞语音听写（流式版）WebSocket API**（`wss://iat-api.xfyun.cn/v2/iat`），HMAC-SHA256 鉴权，16kHz PCM 流式上传，启用 `dwa=wpgs` 动态修正获得字级实时结果 |
| 后台持续录音 | 前台服务 + `foregroundServiceType="microphone"`，声明 `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` 权限（Android 14+ 强制），启动前必须已授予 `RECORD_AUDIO` |
| 录音文件 | 单一 `AudioRecord` 采集 16kHz/16bit/单声道 PCM，一路喂讯飞，一路经 `MediaCodec(AAC)+MediaMuxer` 写 `.m4a`（避免双开麦克风冲突） |
| Android 13+ 通知 | 运行时申请 `POST_NOTIFICATIONS`；前台服务通知带"停止"操作 |
| Android 16 edge-to-edge | 所有页面根布局通过 `WindowInsetsCompat.systemBars()` 应用内边距 |
| 主线程安全 | 识别回调经全局 `RecorderSession` 汇总，统一在主线程通知 UI |

### 代码结构

```
app/src/main/java/com/voicenotes/app/
├── MainActivity.kt            笔记列表
├── RecorderActivity.kt        录音转写页（实时文字/计时/音量）
├── NoteDetailActivity.kt      笔记详情（播放/编辑/分享/删除）
├── SettingsActivity.kt        设置（引擎选择 + 讯飞密钥）
├── model/Note.kt              笔记数据模型
├── data/NoteStore.kt          文件化笔记存储
├── data/Prefs.kt              SharedPreferences 设置
├── record/RecordingService.kt 前台录音服务（总调度）
├── record/XunfeiIatClient.kt  讯飞流式识别客户端
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

## 免责声明

本应用仅作学习与个人使用。讯飞接口调用产生的费用/免费额度以讯飞开放平台规则为准；
请勿在未获他人同意的情况下录音。
