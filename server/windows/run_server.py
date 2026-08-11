# -*- coding: utf-8 -*-
"""VoiceNotes 后端便携启动器：加载 .env 后启动 uvicorn。"""
import os
import sys

# 嵌入式 Python 的 sys.path 不含脚本目录，这里显式加入，确保能 import main
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))


def load_env(path=".env"):
    if not os.path.exists(path):
        return
    with open(path, encoding="utf-8-sig") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            os.environ.setdefault(k.strip(), v.strip())


if __name__ == "__main__":
    load_env()
    import uvicorn

    port = int(os.getenv("PORT", "8000"))
    print(f"[VoiceNotes] 后端启动: http://0.0.0.0:{port}  (Ctrl+C 停止)")
    uvicorn.run("main:app", host="0.0.0.0", port=port, log_level="info")

