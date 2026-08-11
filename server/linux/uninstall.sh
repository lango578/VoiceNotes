#!/usr/bin/env bash
# VoiceNotes 后端 Linux 卸载脚本
# 用法: sudo bash uninstall.sh
set -euo pipefail

SERVICE_NAME="voicenotes-server.service"
APP_DIR="/opt/voicenotes-server"
SERVICE_USER="voicenotes"

if [ "$(id -u)" -ne 0 ]; then
  echo "[错误] 请用 root 运行: sudo bash uninstall.sh"
  exit 1
fi

echo "==> 停止并禁用 systemd 服务"
systemctl stop "${SERVICE_NAME}" 2>/dev/null || true
systemctl disable "${SERVICE_NAME}" 2>/dev/null || true
rm -f "/etc/systemd/system/${SERVICE_NAME}"
systemctl daemon-reload

echo "==> 删除专用系统用户"
userdel "${SERVICE_USER}" 2>/dev/null || true

echo "==> 询问是否删除数据目录"
read -r -p "删除 ${APP_DIR}（含 Whisper 模型缓存，之后需重新下载）？[y/N] " ans
case "${ans}" in
  y|Y|yes|YES) rm -rf "${APP_DIR}"; echo "已删除 ${APP_DIR}";;
  *) echo "保留 ${APP_DIR}";;
esac

echo "卸载完成。"
