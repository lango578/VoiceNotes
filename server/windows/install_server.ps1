# ============================================================
#  VoiceNotes 后端 — Windows 一键安装脚本（建议管理员运行）
#
#  用法:
#    powershell -ExecutionPolicy Bypass -File windows\install_server.ps1
#
#  可选参数:
#    -Port 8000                 监听端口
#    -Whisper                   安装 faster-whisper（启用转写）
#    -WhisperModel small        tiny/base/small/medium/large-v3
#    -ApiKey sk-xxx             DeepSeek 密钥
#    -NoAutoStart               不注册开机自启计划任务
# ============================================================
param(
    [int]$Port = 8000,
    [switch]$Whisper,
    [string]$WhisperModel = 'small',
    [string]$ApiKey = '',
    [switch]$NoAutoStart
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host '==> [1/5] 检查 Python 与 ffmpeg' -ForegroundColor Cyan
$py = (Get-Command python -ErrorAction SilentlyContinue).Source
if (-not $py) {
    Write-Host '[错误] 未找到 python，请从 https://python.org 安装 Python 3.11/3.12 并勾选 Add to PATH' -ForegroundColor Red
    exit 1
}
Write-Host "Python: $py" -ForegroundColor Green
if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    Write-Host '[提示] 未找到 ffmpeg。Whisper 转写需要它，可执行: winget install ffmpeg 后重启终端' -ForegroundColor Yellow
}

Write-Host '==> [2/5] 创建虚拟环境并安装依赖' -ForegroundColor Cyan
python -m venv .venv
& '.\.venv\Scripts\python.exe' -m pip install --upgrade pip
& '.\.venv\Scripts\pip.exe' install -r requirements.txt
if ($Whisper) {
    Write-Host '   安装 faster-whisper（Whisper 转写）...' -ForegroundColor Cyan
    & '.\.venv\Scripts\pip.exe' install faster-whisper
}

Write-Host '==> [3/5] 生成 .env 配置' -ForegroundColor Cyan
if (-not (Test-Path '.env')) {
    $baiduAppId = Read-Host '百度翻译 AppID（不用百度直接回车）'
    $baiduKey = Read-Host '百度翻译密钥（不用百度直接回车）'
    $authToken = Read-Host '后端 Token（可选，App 需填相同值；不用直接回车）'
    $content = @"
TRANSLATE_PROVIDER=deepseek
DEEPSEEK_API_KEY=$ApiKey
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-chat
BAIDU_APPID=$baiduAppId
BAIDU_KEY=$baiduKey
AUTH_TOKEN=$authToken
WHISPER_MODEL=$WhisperModel
WHISPER_DEVICE=auto
WHISPER_COMPUTE_TYPE=int8
"@
    [System.IO.File]::WriteAllText("$root\.env", $content, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "   已生成 $root\.env（可用记事本再改）" -ForegroundColor Green
} else {
    Write-Host '   已存在 .env，保留原配置' -ForegroundColor Yellow
}

Write-Host '==> [4/5] 注册开机自启计划任务' -ForegroundColor Cyan
if (-not $NoAutoStart) {
    $action = New-ScheduledTaskAction -Execute 'powershell.exe' `
        -Argument "-ExecutionPolicy Bypass -File `"$root\windows\run_server.ps1`" -Port $Port" `
        -WorkingDirectory $root
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
    $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit ([TimeSpan]::Zero)
    Register-ScheduledTask -TaskName 'VoiceNotesServer' -Action $action -Trigger $trigger `
        -Settings $settings -Description 'VoiceNotes backend server' -Force | Out-Null
    Write-Host '   计划任务 VoiceNotesServer 已注册（登录后自动启动）' -ForegroundColor Green
}

Write-Host '==> [5/5] 放行防火墙并启动' -ForegroundColor Cyan
try {
    New-NetFirewallRule -DisplayName 'VoiceNotes Server' -Direction Inbound -Protocol TCP -LocalPort $Port -Action Allow | Out-Null
    Write-Host '   防火墙已放行 8000 端口' -ForegroundColor Green
} catch {
    Write-Host "   [提示] 防火墙放行需要管理员权限，请手动执行: New-NetFirewallRule -DisplayName 'VoiceNotes' -Direction Inbound -Protocol TCP -LocalPort $Port -Action Allow" -ForegroundColor Yellow
}

Start-Process powershell.exe -ArgumentList "-ExecutionPolicy Bypass -File `"$root\windows\run_server.ps1`" -Port $Port"
Write-Host ''
Write-Host '完成！启动日志见 server.log（PowerShell 窗口内）。' -ForegroundColor Green
Write-Host '手机 App 设置：识别引擎=自建后端识别(Whisper)，后端地址=http://<本机局域网IP>:8000' -ForegroundColor Green
Write-Host "本机局域网IP: $(Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } | Select-Object -First 1 -ExpandProperty IPAddress)" -ForegroundColor Green
