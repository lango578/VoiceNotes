# 下载 Gradle Wrapper 启动 jar（gradle-wrapper.jar）
# 用途：Android Studio 打开本项目时，如果提示 "Gradle wrapper not found"，
#       先在本目录运行此脚本，然后再打开项目 / 点击 Sync。
# 要求：本机可访问互联网（GitHub 或 Gitee 镜像，任选其一）。
# 注意：gradle-wrapper.jar 是二进制文件，必须完整下载，请勿用文本编辑器生成。

$ErrorActionPreference = 'Stop'
$dest = Join-Path $PSScriptRoot 'gradle\wrapper\gradle-wrapper.jar'

function Download-Jar([string]$url) {
    Write-Host "正在下载: $url"
    Invoke-WebRequest -Uri $url -OutFile $dest -UseBasicParsing -TimeoutSec 60
    $len = (Get-Item $dest).Length
    if ($len -lt 50000) { throw "下载的文件大小异常: $len 字节" }
    Write-Host "OK! gradle-wrapper.jar 已保存 ($len 字节)"
}

if (Test-Path $dest) {
    $len = (Get-Item $dest).Length
    if ($len -gt 50000) {
        Write-Host "gradle-wrapper.jar 已存在 ($len 字节)，无需重复下载。"
        exit 0
    }
}

try {
    # 首选 GitHub 官方仓库
    Download-Jar 'https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar'
} catch {
    Write-Host "GitHub 下载失败: $($_.Exception.Message)"
    try {
        # 备选 Gitee 镜像
        Download-Jar 'https://gitee.com/mirrors/gradle/raw/v8.11.1/gradle/wrapper/gradle-wrapper.jar'
    } catch {
        Write-Host "Gitee 下载失败: $($_.Exception.Message)"
        Write-Host "请手动下载 gradle-wrapper.jar（Gradle 8.11.1 发行版自带），"
        Write-Host "放到: $dest"
        exit 1
    }
}
