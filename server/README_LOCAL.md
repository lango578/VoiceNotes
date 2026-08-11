# VoiceNotes 后端 — Windows / macOS 本机部署教程
---

## 4. 配置详解（.env）

在 `server/` 目录创建 `.env`（UTF-8 无 BOM，`KEY=VALUE` 一行一个，**不要加引号**）：

```ini
# 翻译后端：deepseek（默认）或 baidu
TRANSLATE_PROVIDER=deepseek
DEEPSEEK_API_KEY=sk-xxxx
# DEEPSEEK_BASE_URL=https://api.deepseek.com
# DEEPSEEK_MODEL=deepseek-chat
# 百度翻译（用 baidu 时填写）：
# BAIDU_APPID=
# BAIDU_KEY=

# 可选：接口鉴权，App 需填相同「后端 Token」
AUTH_TOKEN=

# Whisper（可选）：tiny/base/small/medium/large-v3
WHISPER_MODEL=small
WHISPER_DEVICE=auto
WHISPER_COMPUTE_TYPE=int8
```

`run_server.ps1` / `install_macos.sh` 会自动把 `.env` 加载为进程环境变量。

---

## 5. 自检命令

```bash
# 健康检查（Windows PowerShell 或 macOS 终端通用）
curl http://127.0.0.1:8000/health

# 翻译测试
curl -X POST http://127.0.0.1:8000/api/annotate -H "Content-Type: application/json" `
  -d '{\"text\":\"Hello world. This is a voice note.\"}'

# 局域网地址（macOS：ipconfig getifaddr en0；Windows：ipconfig）
# 手机设置里的后端地址填 http://<局域网IP>:8000
```

---

## 6. 常见问题（Windows/macOS）

| 现象 | 处理 |
|---|---|
| `'python' 不是内部或外部命令` | 重装 Python 并勾选 **Add to PATH**，重启终端 |
| 端口被占用 `Address already in use` | `netstat -ano \| findstr :8000`（Win）`lsof -i :8000`（mac）查占用，改 `.env` 端口 |
| 手机连不上电脑 | ① 电脑确认监听 `0.0.0.0`；② Windows 防火墙放行 8000（§2.4）；③ macOS 允许传入连接；④ 同一 Wi-Fi、路由器未开 AP 隔离 |
| macOS 报 `ffmpeg: command not found` | `brew install ffmpeg` |
| Windows 报 ffmpeg 相关错误 | `winget install ffmpeg`，然后**重启终端/电脑**让 PATH 生效 |
| 首次转写卡住 | 正在下载 Whisper 模型（约 460 MB）；国内网络设 `HF_ENDPOINT=https://hf-mirror.com` 后重启 |
| `.env` 改了不生效 | 重启服务（计划任务/launchd 重新启动） |
| 电池/锁屏后服务被停 | 计划任务/launchd 已配 `KeepAlive`；Windows 笔记本注意电源计划勿深度休眠 |

---


把后端跑在你自己的**电脑**上（Windows 或 macOS），手机与电脑同一局域网即可使用「自建后端识别（Whisper，边录边出字）」与「后端翻译（英文→中文注释）」。

> Linux 部署（systemd 开机自启、GPU 加速）请看 [README_LINUX.md](README_LINUX.md)；跨地域组网请看 [README_TAILSCALE.md](README_TAILSCALE.md)。

---

## 目录

1. [系统需求](#1-系统需求)
2. [Windows 部署](#2-windows-部署)
3. [macOS 部署](#3-macos-部署)
4. [配置详解（.env）](#4-配置详解env)
5. [自检命令](#5-自检命令)
6. [常见问题（Windows/macOS）](#6-常见问题windowsmacos)

---

## 1. 系统需求

| 项目 | 要求 |
|---|---|
| 操作系统 | Windows 10/11 64 位；macOS 12+（Apple Silicon 或 Intel 均可） |
| Python | 3.10 ~ 3.12（安装时勾选 **Add to PATH**） |
| ffmpeg | 开启 Whisper 转写时必需（解码 `.m4a`） |
| 内存 | 仅翻译 512 MB；Whisper `tiny/base` ~2 GB，`small` ~3 GB |
| 网络 | 电脑能访问 DeepSeek / 百度翻译 API；手机与电脑同一局域网 |

> GPU 加速（NVIDIA CUDA / Apple Silicon MPS）：Windows + NVIDIA 显卡可参考 [README_LINUX.md §6.2](README_LINUX.md)；macOS Apple Silicon 用 `WHISPER_DEVICE=cpu`（faster-whisper 原生支持 MPS 实验性，暂不建议）。

---

## 2. Windows 部署

### 2.0 便携包（推荐，解压即用）

下载 **VoiceNotes-server-windows.zip**（GitHub Release 页，内置 Python 与全部依赖，**无需安装 Python/ffmpeg**）：

1. 解压到电脑任意位置（路径最好不含中文/空格），得到文件夹 `voicenotes-server-windows\`
2. 用记事本打开 `.env`，填入 `DEEPSEEK_API_KEY`（DeepSeek 密钥，英文→中文注释用；只转写可留空）
3. 双击 **`start.bat`** 启动服务
4. 浏览器打开 `http://127.0.0.1:8000/health`，看到 `{"status":"ok","provider":"deepseek"}` 即成功
5. 手机 App：识别引擎=自建后端识别（Whisper），后端地址=`http://<电脑局域网IP>:8000`
6. （可选）双击 `install_autostart.bat` 注册开机自启；`uninstall_autostart.bat` 取消

> 首次使用 Whisper 转写会自动下载模型（small 约 460MB，需联网，仅一次）。
> 国内网络下载慢时在 `.env` 末尾加 `HF_ENDPOINT=https://hf-mirror.com` 后重启。
> 打包与自检脚本：`server/windows/build_portable.ps1`。

### 2.1 一键脚本（开发/自建）

把仓库 `server/` 目录放到电脑上，例如 `D:\server`，然后以 **管理员** PowerShell 运行：

```powershell
cd D:\server
Set-ExecutionPolicy -Scope Process Bypass
# 先设定密钥（可选）
$env:DEEPSEEK_API_KEY = 'sk-xxxx'
# 需要 Whisper 转写时加开关：
.\windows\install_server.ps1 -Whisper -WhisperModel small
```

脚本自动完成：装依赖（含 faster-whisper）→ 生成 `.env` → 注册**开机自启计划任务** `VoiceNotesServer` → 放行防火墙 8000 端口 → 立即启动。

启动日志输出到 `D:\server\server.log`。

### 2.2 手动步骤

```powershell
# 1) 安装 Python 3.11（官网 https://python.org 下载，勾选 Add to PATH）与 ffmpeg
winget install ffmpeg

# 2) 进入 server 目录，创建虚拟环境
cd D:\server
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\pip.exe install -r requirements.txt
# 开启 Whisper 时再加一行：
.\.venv\Scripts\pip.exe install faster-whisper

# 3) 生成配置（或用记事本手动建 .env，参考 §4）
# 4) 前台运行测试
.\.venv\Scripts\python.exe -m uvicorn main:app --host 0.0.0.0 --port 8000
# Ctrl+C 停止；确认无误后再做开机自启（§2.3）
```

### 2.3 Windows 开机自启（计划任务）

```powershell
$action = New-ScheduledTaskAction -Execute 'D:\server\.venv\Scripts\python.exe' `
  -Argument '-m uvicorn main:app --host 0.0.0.0 --port 8000' -WorkingDirectory 'D:\server'
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit 0
Register-ScheduledTask -TaskName 'VoiceNotesServer' -Action $action -Trigger $trigger `
  -Settings $settings -Description 'VoiceNotes backend server' -Force
Start-ScheduledTask -TaskName 'VoiceNotesServer'   # 立即启动
```

> 注意：后端通过 `run_server.ps1` 启动时会自动把 `.env` 读入环境变量；计划任务直接调 uvicorn 时**读不到 .env**。
> 建议计划任务改为执行 `D:\server\windows\run_server.ps1`，或把密钥配置成系统环境变量。
> 用下面的命令把计划任务指向脚本：
> ```powershell
> $action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument '-ExecutionPolicy Bypass -File D:\server\windows\run_server.ps1' -WorkingDirectory 'D:\server'
> Register-ScheduledTask -TaskName 'VoiceNotesServer' -Action $action -Trigger $trigger -Settings $settings -Force
> ```

### 2.4 Windows 防火墙

```powershell
New-NetFirewallRule -DisplayName 'VoiceNotes 8000' -Direction Inbound -Protocol TCP -LocalPort 8000 -Action Allow
```

（一键脚本已自动处理。）

---

## 3. macOS 部署

### 3.1 一键脚本

```bash
cd server
./macos/install_macos.sh
# 可选环境变量：DEEPSEEK_API_KEY=sk-xxx  ENABLE_WHISPER=yes  WHISPER_MODEL=small
```

脚本自动：装 `brew` 依赖（python@3.12、ffmpeg）→ venv + 依赖 → 生成 `.env` → 写 `~/Library/LaunchAgents/com.voicenotes.server.plist` → `launchctl` 加载并启动（登录后自动运行）。

### 3.2 手动步骤

```bash
# 1) 安装依赖
brew install python@3.12 ffmpeg

# 2) 虚拟环境
cd server
/opt/homebrew/bin/python3.12 -m venv .venv     # Apple Silicon 路径；Intel 用 /usr/local/bin/python3.12
.venv/bin/pip install --upgrade pip
.venv/bin/pip install -r requirements.txt
.venv/bin/pip install faster-whisper          # 开启 Whisper 时

# 3) 生成 .env（参考 §4）

# 4) 前台测试
.venv/bin/python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

### 3.3 macOS 开机自启（launchd）

`macos/install_macos.sh` 会自动生成 plist。手动版：

```bash
cat > ~/Library/LaunchAgents/com.voicenotes.server.plist <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key><string>com.voicenotes.server</string>
    <key>ProgramArguments</key>
    <array>
        <string>/Users/USERNAME/server/.venv/bin/python</string>
        <string>-m</string><string>uvicorn</string>
        <string>main:app</string>
        <string>--host</string><string>0.0.0.0</string>
        <string>--port</string><string>8000</string>
    </array>
    <key>WorkingDirectory</key><string>/Users/USERNAME/server</string>
    <key>EnvironmentVariables</key>
    <dict>
        <key>TRANSLATE_PROVIDER</key><string>deepseek</string>
        <key>DEEPSEEK_API_KEY</key><string>sk-xxxx</string>
        <key>AUTH_TOKEN</key><string></string>
        <key>WHISPER_MODEL</key><string>small</string>
        <key>WHISPER_DEVICE</key><string>auto</string>
        <key>WHISPER_COMPUTE_TYPE</key><string>int8</string>
    </dict>
    <key>RunAtLoad</key><true/>
    <key>KeepAlive</key><true/>
    <key>StandardOutPath</key><string>/tmp/voicenotes-server.log</string>
    <key>StandardErrorPath</key><string>/tmp/voicenotes-server.err.log</string>
</dict>
</plist>
EOF
# 把 USERNAME 换成你的用户名后：
launchctl unload ~/Library/LaunchAgents/com.voicenotes.server.plist 2>/dev/null
launchctl load ~/Library/LaunchAgents/com.voicenotes.server.plist
launchctl start com.voicenotes.server
```

管理命令：

```bash
launchctl list | grep voicenotes        # 是否在运行
cat /tmp/voicenotes-server.err.log      # 错误日志
launchctl unload ~/Library/LaunchAgents/com.voicenotes.server.plist   # 停止自启
```

> macOS 首次运行会弹「是否允许接受传入网络连接」，点允许；否则手机连不上。
