# 本地构建脚本（Windows）：
# 在你自己的电脑上（已装 JDK 17 + Android SDK，或用 Android Studio）构建 debug/release APK 并复制到 dist/。
# 用法：powershell -ExecutionPolicy Bypass -File build_apk.ps1
#
# 说明：
# - 依赖 GRADLE_HOME 或系统 gradle；也可以直接让本脚本调用 ./gradlew（需要 gradle-wrapper.jar）
# - Android SDK 路径从 local.properties 读取，或设置 ANDROID_HOME
$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
Set-Location $root

if (-not (Test-Path 'gradle\wrapper\gradle-wrapper.jar')) {
    Write-Host '缺少 gradle/wrapper/gradle-wrapper.jar，先运行 setup-gradle-wrapper.ps1'
    exit 1
}

& .\gradlew.bat --no-daemon assembleDebug assembleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Host '构建失败'
    exit $LASTEXITCODE
}

New-Item -ItemType Directory -Force -Path (Join-Path $root 'dist') | Out-Null
Copy-Item 'app\build\outputs\apk\debug\app-debug.apk'   (Join-Path $root 'dist\VoiceNotes-debug.apk')   -Force
if (Test-Path 'app\build\outputs\apk\release\app-release.apk') {
    Copy-Item 'app\build\outputs\apk\release\app-release.apk' (Join-Path $root 'dist\VoiceNotes-release.apk') -Force
}
Write-Host '完成，APK 位于 dist/ 目录。'
