#!/usr/bin/env bash
# ============================================================
#  VoiceNotes 后端 — macOS 一键安装脚本（含 launchd 开机自启）
#  用法: bash macos/install_macos.sh
#  可选环境变量:
#    DEEPSEEK_API_KEY=sk-xxx
#    ENABLE_WHISPER=yes
#    WHISPER_MODEL=small
#    PORT=8000
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"
PORT="${PORT:-8000}"
ENABLE_WHISPER="${ENABLE_WHISPER:-no}"
WHISPER_MODEL="${WHISPER_MODEL:-small}"
LABEL="com.voicenotes.server"
PLIST="$HOME/Library/LaunchAgents/${LABEL}.plist"

echo "==> [1/5] 检查/安装 brew 依赖 (python@3.12, ffmpeg)"
if ! command -v brew >/dev/null 2>&1; then
  echo "[错误] 未找到 Homebrew，请先安装: /bin/bash -c \"$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
  exit 1
fi
brew install python@3.12 ffmpeg

PY="$(brew --prefix)/opt/python@3.12/bin/python3.12"
if [ ! -x "${PY}" ]; then PY="$(brew --prefix)/bin/python3.12"; fi

echo "==> [2/5] 创建虚拟环境并安装依赖"
python3 -m venv .venv
.venv/bin/pip install --upgrade pip
.venv/bin/pip install -r requirements.txt
if [ "${ENABLE_WHISPER}" = "yes" ]; then
  echo "    安装 faster-whisper（Whisper 转写）..."
  .venv/bin/pip install faster-whisper
fi

echo "==> [3/5] 生成 .env"
if [ -f ".env" ]; then
  echo "    已存在 .env，保留原配置"
else
  cat > .env <<EOF
TRANSLATE_PROVIDER=deepseek
DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY:-}
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-chat
BAIDU_APPID=${BAIDU_APPID:-}
BAIDU_KEY=${BAIDU_KEY:-}
AUTH_TOKEN=${AUTH_TOKEN:-}
WHISPER_MODEL=${WHISPER_MODEL}
WHISPER_DEVICE=auto
WHISPER_COMPUTE_TYPE=int8
EOF
  chmod 600 .env
  echo "    已生成 .env（请检查 DEEPSEEK_API_KEY）"
fi

echo "==> [4/5] 写入 launchd plist (${PLIST})"
set +e
while IFS='=' read -r k v; do
  case "$k" in
    TRANSLATE_PROVIDER|DEEPSEEK_API_KEY|DEEPSEEK_BASE_URL|DEEPSEEK_MODEL|BAIDU_APPID|BAIDU_KEY|AUTH_TOKEN|WHISPER_MODEL|WHISPER_DEVICE|WHISPER_COMPUTE_TYPE)
      : ;;
    *) continue ;;
  esac
  eval "export $k=\"\$v\""
done < .env
set -e

cat > "${PLIST}" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key><string>${LABEL}</string>
    <key>ProgramArguments</key>
    <array>
        <string>${ROOT}/.venv/bin/python</string>
        <string>-m</string><string>uvicorn</string>
        <string>main:app</string>
        <string>--host</string><string>0.0.0.0</string>
        <string>--port</string><string>${PORT}</string>
    </array>
    <key>WorkingDirectory</key><string>${ROOT}</string>
    <key>EnvironmentVariables</key>
    <dict>
        <key>TRANSLATE_PROVIDER</key><string>${TRANSLATE_PROVIDER:-deepseek}</string>
        <key>DEEPSEEK_API_KEY</key><string>${DEEPSEEK_API_KEY:-}</string>
        <key>DEEPSEEK_BASE_URL</key><string>${DEEPSEEK_BASE_URL:-https://api.deepseek.com}</string>
        <key>DEEPSEEK_MODEL</key><string>${DEEPSEEK_MODEL:-deepseek-chat}</string>
        <key>BAIDU_APPID</key><string>${BAIDU_APPID:-}</string>
        <key>BAIDU_KEY</key><string>${BAIDU_KEY:-}</string>
        <key>AUTH_TOKEN</key><string>${AUTH_TOKEN:-}</string>
        <key>WHISPER_MODEL</key><string>${WHISPER_MODEL}</string>
        <key>WHISPER_DEVICE</key><string>auto</string>
        <key>WHISPER_COMPUTE_TYPE</key><string>int8</string>
    </dict>
    <key>RunAtLoad</key><true/>
    <key>KeepAlive</key><true/>
    <key>StandardOutPath</key><string>/tmp/${LABEL}.log</string>
    <key>StandardErrorPath</key><string>/tmp/${LABEL}.err.log</string>
</dict>
</plist>
EOF

echo "==> [5/5] 加载并启动服务"
launchctl unload "${PLIST}" 2>/dev/null || true
launchctl load "${PLIST}"
launchctl start "${LABEL}"

sleep 2
echo "------------------------------------------------------------"
curl -s "http://127.0.0.1:${PORT}/health" && echo "" || {
  echo "[警告] /health 暂不可用，请查看: cat /tmp/${LABEL}.err.log"
}
echo "完成！手机 App 后端地址: http://<本机局域网IP>:${PORT}   (本机IP: $(ipconfig getifaddr en0 2>/dev/null || echo '请用 ipconfig getifaddr en0 查询'))"
echo "管理命令: launchctl list | grep voicenotes   |   日志: cat /tmp/${LABEL}.err.log"
echo "卸载自启: launchctl unload ${PLIST}"
