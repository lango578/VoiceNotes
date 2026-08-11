#!/usr/bin/env bash
# ============================================================
#  VoiceNotes 后端 — Linux 一键部署脚本
#  支持: Ubuntu / Debian / CentOS / Rocky / AlmaLinux / Fedora
#
#  用法:
#     sudo bash install.sh
#
#  可提前 export 的环境变量（均为可选，默认值如下）:
#     PORT=8000                      监听端口
#     ENABLE_WHISPER=no              是否安装 faster-whisper（yes 启用 /api/transcribe）
#     WHISPER_MODEL=small            tiny/base/small/medium/large-v3
#     WHISPER_DEVICE=auto            auto/cpu/cuda
#     WHISPER_COMPUTE_TYPE=int8      int8/float16（cuda 建议 float16）
#     TRANSLATE_PROVIDER=deepseek    deepseek/baidu
#     DEEPSEEK_API_KEY=sk-xxx        DeepSeek 密钥（必填，或用百度）
#     AUTH_TOKEN=                    （可选）接口鉴权 Token
# ============================================================
set -euo pipefail

APP_NAME="voicenotes-server"
APP_DIR="/opt/${APP_NAME}"
SERVICE_USER="voicenotes"
SERVICE_NAME="${APP_NAME}.service"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}"

PORT="${PORT:-8000}"
ENABLE_WHISPER="${ENABLE_WHISPER:-no}"
WHISPER_MODEL="${WHISPER_MODEL:-small}"
WHISPER_DEVICE="${WHISPER_DEVICE:-auto}"
WHISPER_COMPUTE_TYPE="${WHISPER_COMPUTE_TYPE:-int8}"
TRANSLATE_PROVIDER="${TRANSLATE_PROVIDER:-deepseek}"

if [ "$(id -u)" -ne 0 ]; then
  echo "[错误] 请用 root 运行: sudo bash install.sh"
  exit 1
fi

echo "==> [1/6] 检测包管理器并安装基础依赖 (python3 / python3-venv / ffmpeg / curl)"
if command -v apt-get >/dev/null 2>&1; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y python3 python3-venv python3-pip ffmpeg curl
elif command -v dnf >/dev/null 2>&1; then
  dnf install -y epel-release curl || true
  dnf install -y python3 python3-pip curl ffmpeg || dnf install -y python3 python3-pip curl
elif command -v yum >/dev/null 2>&1; then
  yum install -y epel-release curl || true
  yum install -y python3 python3-pip curl ffmpeg || yum install -y python3 python3-pip curl
else
  echo "[错误] 未识别的包管理器，请手动安装 python3 / python3-venv / ffmpeg 后重试"
  exit 1
fi

echo "==> [2/6] 创建目录与专用系统用户"
mkdir -p "${APP_DIR}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 定位服务端源码：依次查找 脚本同目录 / 上级目录(server/) / 已存在的安装目录
SRC=""
if [ -f "${SCRIPT_DIR}/main.py" ]; then SRC="${SCRIPT_DIR}"; fi
if [ -z "${SRC}" ] && [ -f "${SCRIPT_DIR}/../main.py" ]; then SRC="$(cd "${SCRIPT_DIR}/.." && pwd)"; fi
if [ -z "${SRC}" ] && [ -f "${APP_DIR}/main.py" ]; then SRC="${APP_DIR}"; fi
if [ -n "${SRC}" ]; then
  cp -f "${SRC}/main.py" "${APP_DIR}/"
  [ -f "${SRC}/requirements.txt" ] && cp -f "${SRC}/requirements.txt" "${APP_DIR}/"
  echo "[提示] 服务端源码来自 ${SRC}"
else
  echo "[错误] 未找到 main.py。请把 server/ 目录整体拷贝到本机后重试，或手动将 main.py / requirements.txt 放入 ${APP_DIR}"
  exit 1
fi
if ! id "${SERVICE_USER}" >/dev/null 2>&1; then
  useradd --system --home-dir "${APP_DIR}" --shell /usr/sbin/nologin "${SERVICE_USER}"
fi
mkdir -p "${APP_DIR}/.cache"
chown -R "${SERVICE_USER}:${SERVICE_USER}" "${APP_DIR}"

echo "==> [3/6] 创建 Python 虚拟环境并安装依赖"
python3 -m venv "${APP_DIR}/venv"
"${APP_DIR}/venv/bin/pip" install --upgrade pip
"${APP_DIR}/venv/bin/pip" install -r "${APP_DIR}/requirements.txt"
if [ "${ENABLE_WHISPER}" = "yes" ]; then
  echo "==> [信息] 安装 faster-whisper（启用 /api/transcribe 转写）"
  "${APP_DIR}/venv/bin/pip" install faster-whisper
fi

echo "==> [4/6] 生成 .env 配置"
ENV_FILE="${APP_DIR}/.env"
if [ -f "${ENV_FILE}" ]; then
  echo "[提示] 已存在 ${ENV_FILE}，保留原配置（如需修改请手动编辑）"
else
  cat > "${ENV_FILE}" <<EOF
TRANSLATE_PROVIDER=${TRANSLATE_PROVIDER}
DEEPSEEK_API_KEY=${DEEPSEEK_API_KEY:-}
DEEPSEEK_BASE_URL=${DEEPSEEK_BASE_URL:-https://api.deepseek.com}
DEEPSEEK_MODEL=${DEEPSEEK_MODEL:-deepseek-chat}
BAIDU_APPID=${BAIDU_APPID:-}
BAIDU_KEY=${BAIDU_KEY:-}
AUTH_TOKEN=${AUTH_TOKEN:-}
WHISPER_MODEL=${WHISPER_MODEL}
WHISPER_DEVICE=${WHISPER_DEVICE}
WHISPER_COMPUTE_TYPE=${WHISPER_COMPUTE_TYPE}
EOF
  chmod 600 "${ENV_FILE}"
  echo "[提示] 已生成 ${ENV_FILE}，请确认 DEEPSEEK_API_KEY / AUTH_TOKEN 是否正确"
fi
chown "${SERVICE_USER}:${SERVICE_USER}" "${ENV_FILE}"

echo "==> [5/6] 安装 systemd 服务"
cat > "${SERVICE_FILE}" <<EOF
[Unit]
Description=VoiceNotes backend server (translation + optional Whisper ASR)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${SERVICE_USER}
Group=${SERVICE_USER}
WorkingDirectory=${APP_DIR}
EnvironmentFile=${ENV_FILE}
Environment=HF_HOME=${APP_DIR}/.cache
ExecStart=${APP_DIR}/venv/bin/uvicorn main:app --host 0.0.0.0 --port ${PORT} --workers 1
Restart=on-failure
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable --now "${SERVICE_NAME}"
systemctl restart "${SERVICE_NAME}"

echo "==> [6/6] 自检"
sleep 2
systemctl --no-pager --full status "${SERVICE_NAME}" | head -n 8 || true
echo "------------------------------------------------------------"
echo "服务状态: $(systemctl is-active ${SERVICE_NAME})"
curl -s "http://127.0.0.1:${PORT}/health" && echo "" || echo "[警告] /health 暂不可用，请查看: journalctl -u ${SERVICE_NAME} -n 50"
echo ""
echo "部署完成！手机 App 设置："
echo "  · 识别引擎 = 自建后端识别（Whisper）时，后端地址填 http://<本机局域网IP>:${PORT}"
echo "  · 注释模式 = 后端翻译 时，后端地址同上"
if [ -n "${AUTH_TOKEN:-}" ]; then echo "  · 后端 Token = ${AUTH_TOKEN}"; fi
echo ""
echo "常用命令："
echo "  systemctl status ${SERVICE_NAME}    # 查看状态"
echo "  journalctl -u ${SERVICE_NAME} -f    # 跟踪日志"
echo "  systemctl restart ${SERVICE_NAME}   # 重启"
echo ""
echo "卸载: sudo bash $(dirname "${BASH_SOURCE[0]}")/uninstall.sh"
