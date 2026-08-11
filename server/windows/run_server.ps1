# ============================================================
#  VoiceNotes 后端 — Windows 启动脚本
#  用法: powershell -ExecutionPolicy Bypass -File windows\run_server.ps1
#  会把 .env 加载为进程环境变量后启动 uvicorn。
# ============================================================
param([int]$Port = 8000)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path '.venv\Scripts\python.exe')) {
    Write-Host '[错误] 未找到 .venv，请先运行 windows\install_server.ps1' -ForegroundColor Red
    exit 1
}

if (Test-Path '.env') {
    Get-Content '.env' | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
        $kv = $_ -split '=', 2
        [Environment]::SetEnvironmentVariable($kv[0].Trim(), $kv[1].Trim(), 'Process')
    }
    Write-Host "[信息] 已加载 .env 配置" -ForegroundColor Green
} else {
    Write-Host '[提示] 未找到 .env，将以默认配置启动（翻译/转写需先配置）' -ForegroundColor Yellow
}

Write-Host "启动 VoiceNotes 后端: http://0.0.0.0:$Port  (Ctrl+C 停止)" -ForegroundColor Green
& '.\.venv\Scripts\python.exe' -m uvicorn main:app --host 0.0.0.0 --port $Port
