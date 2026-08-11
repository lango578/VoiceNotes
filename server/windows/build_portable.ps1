# ============================================================
#  VoiceNotes 后端 — Windows 便携包构建脚本（解压即用 ZIP）
#
#  功能：
#   1. 下载 Python 嵌入式发行版（无需安装）
#   2. 内置 pip 并安装全部依赖（fastapi/uvicorn/numpy/faster-whisper）
#   3. 拷贝服务端代码与启动脚本
#   4. 打包为 VoiceNotes-server-windows.zip
#
#  用法：
#    powershell -ExecutionPolicy Bypass -File windows\build_portable.ps1
#
#  可选参数：
#    -PythonVersion 3.12.10       嵌入式 Python 版本
#    -OutDir D:\portable          输出目录（默认 dist）
# ============================================================
param(
    [string]$PythonVersion = '3.12.10',
    [string]$OutDir = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $env:TEMP 'voicenotes-portable-build'
$app = Join-Path $build 'app'
$dl  = Join-Path $build 'dl'
if ($OutDir -eq '') { $OutDir = Join-Path $root 'dist' }

New-Item -ItemType Directory -Force -Path $app, $dl, $OutDir | Out-Null

# ---------- 1. 下载嵌入式 Python ----------
Write-Host '==> [1/6] 下载嵌入式 Python' -ForegroundColor Cyan
$pyUrl = "https://www.python.org/ftp/python/$PythonVersion/python-$PythonVersion-embed-amd64.zip"
$pyZip = Join-Path $dl 'pyembed.zip'
for ($i = 1; $i -le 5; $i++) {
    curl.exe -s -L --retry-all-errors --retry 3 -o $pyZip $pyUrl
    if ((Get-Item $pyZip).Length -gt 5000000) { break }
    Start-Sleep -Seconds 2
}
Expand-Archive -Force $pyZip (Join-Path $app 'python')

# 启用 site-packages（. _pth 加 import site）
$pth = Get-ChildItem (Join-Path $app 'python') -Filter 'python*._pth' | Select-Object -First 1
[IO.File]::WriteAllText($pth.FullName,
    "$($pth.Name -replace '\._pth$', '.zip')`n.`nLib\site-packages`n`n# Uncomment to run site.main() automatically`nimport site`n",
    (New-Object System.Text.UTF8Encoding($false)))

# ---------- 2. 内置 pip ----------
Write-Host '==> [2/6] 内置 pip' -ForegroundColor Cyan
$pipJson = curl.exe -s 'https://pypi.org/pypi/pip/json'
$pipUrl = ($pipJson | ConvertFrom-Json).urls | Where-Object { $_.packagetype -eq 'bdist_wheel' } | Select-Object -First 1 -ExpandProperty url
$pipWhl = Join-Path $dl 'pip.whl'
for ($i = 1; $i -le 8; $i++) {
    curl.exe -s -L --retry-all-errors --retry 3 -o $pipWhl $pipUrl
    $sz = (Get-Item $pipWhl).Length
    if ($sz -gt 1000000) { break }
    Start-Sleep -Seconds 2
}
$pipZip = Join-Path $dl 'pip.zip'
Copy-Item $pipWhl $pipZip -Force
Expand-Archive -Force $pipZip (Join-Path $app 'python\Lib\site-packages')

# ---------- 3. 安装依赖 ----------
Write-Host '==> [3/6] 安装依赖（含 faster-whisper，需联网，耐心等待）' -ForegroundColor Cyan
$py = Join-Path $app 'python\python.exe'
& $py -m pip install --retries 20 --timeout 90 fastapi 'uvicorn[standard]' requests python-multipart numpy
& $py -m pip install --retries 20 --timeout 90 faster-whisper

# ---------- 4. 拷贝服务端代码 ----------
Write-Host '==> [4/6] 拷贝服务端代码' -ForegroundColor Cyan
Copy-Item (Join-Path $root 'main.py') $app
Copy-Item (Join-Path $root 'requirements.txt') $app
Copy-Item (Join-Path $root 'windows\run_server.py') $app
Copy-Item (Join-Path $root 'windows\start.bat') $app
Copy-Item (Join-Path $root 'windows\install_autostart.bat') $app
Copy-Item (Join-Path $root 'windows\uninstall_autostart.bat') $app
Copy-Item (Join-Path $root 'windows\使用说明.txt') $app
if (-not (Test-Path (Join-Path $app '.env'))) {
    Copy-Item (Join-Path $root 'windows\.env.template') (Join-Path $app '.env')
}

# ---------- 5. 自检：启动 + /health ----------
Write-Host '==> [5/6] 自检' -ForegroundColor Cyan
$srvOut = Join-Path $dl 'srv_out.txt'
$srvErr = Join-Path $dl 'srv_err.txt'
$p = Start-Process $py -ArgumentList 'run_server.py' -WorkingDirectory $app -RedirectStandardOutput $srvOut -RedirectStandardError $srvErr -WindowStyle Hidden -PassThru
Start-Sleep -Seconds 6
$health = curl.exe -s -w '|%{http_code}' 'http://127.0.0.1:8000/health'
Write-Host "    /health => $health" -ForegroundColor Green
Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1

# ---------- 6. 打包 ----------
Write-Host '==> [6/6] 打包 ZIP' -ForegroundColor Cyan
$stage = Join-Path $build 'stage\voicenotes-server-windows'
if (Test-Path (Join-Path $build 'stage')) { Remove-Item (Join-Path $build 'stage') -Recurse -Force }
New-Item -ItemType Directory -Force -Path $stage | Out-Null
Copy-Item (Join-Path $app '*') $stage -Recurse -Force
$zipOut = Join-Path $OutDir 'VoiceNotes-server-windows.zip'
if (Test-Path $zipOut) { Remove-Item $zipOut -Force }
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zipOut -CompressionLevel Optimal
Write-Host "完成！便携包: $zipOut" -ForegroundColor Green
Write-Host "大小: $((Get-Item $zipOut).Length / 1MB) MB"
