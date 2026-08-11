# -*- coding: utf-8 -*-
"""
VoiceNotes 自建后端服务
========================
为"语音笔记"App 提供：
- POST /api/translate  英文 → 中文（整段）
- POST /api/annotate   英文 → 逐句中文注释（返回 annotated 文本 + sentences 列表）
- POST /api/transcribe 自托管 Whisper 转写（可选，需安装 faster-whisper，类似 WhisperLiveKit）
- GET  /health         健康检查

翻译后端可通过环境变量切换（TRANSLATE_PROVIDER）：
- deepseek / openai（默认）：OpenAI 兼容 Chat API（DeepSeek、Kimi、通义、智谱等均可用）
- baidu：百度翻译 API（免费额度）

运行：
    pip install -r requirements.txt
    uvicorn main:app --host 0.0.0.0 --port 8000
或使用 Docker：见 README。
"""

import os
import re
import hashlib
import random
import tempfile
from typing import List, Optional

import requests
from fastapi import FastAPI, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

# ---------------- 配置（环境变量） ----------------

TRANSLATE_PROVIDER = os.getenv("TRANSLATE_PROVIDER", "deepseek")  # deepseek | baidu

# OpenAI 兼容（DeepSeek 等）
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

# 百度翻译
BAIDU_APPID = os.getenv("BAIDU_APPID", "")
BAIDU_KEY = os.getenv("BAIDU_KEY", "")

# 可选鉴权：设置 AUTH_TOKEN 后，请求需带 Authorization: Bearer <token>
AUTH_TOKEN = os.getenv("AUTH_TOKEN", "")

# 可选 Whisper（类似 WhisperLiveKit）
WHISPER_MODEL_NAME = os.getenv("WHISPER_MODEL", "small")
_whisper_model = None

app = FastAPI(title="VoiceNotes Server", version="1.0.0")

# App 直连，放开跨域
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---------------- 工具 ----------------

def _check_auth(authorization: Optional[str]) -> None:
    if AUTH_TOKEN and (authorization or "") != f"Bearer {AUTH_TOKEN}":
        raise PermissionError("AUTH_TOKEN 不匹配")

def split_sentences(text: str) -> List[str]:
    """按句子结束标点切分，保留标点。"""
    parts = re.split(r"(?<=[.!?。！？])\s+", text.strip())
    return [p.strip() for p in parts if p.strip()]

def _translate_deepseek(text: str) -> str:
    resp = requests.post(
        f"{DEEPSEEK_BASE_URL.rstrip('/')}/chat/completions",
        headers={"Authorization": f"Bearer {DEEPSEEK_API_KEY}"},
        json={
            "model": DEEPSEEK_MODEL,
            "messages": [
                {
                    "role": "system",
                    "content": (
                        "You are a professional translator. Translate the user's English text "
                        "into natural Simplified Chinese (简体中文). Output ONLY the Chinese "
                        "translation, with no explanations or extra text."
                    ),
                },
                {"role": "user", "content": text},
            ],
            "temperature": 0.3,
        },
        timeout=60,
    )
    resp.raise_for_status()
    return resp.json()["choices"][0]["message"]["content"].strip()

def _translate_baidu(text: str) -> str:
    if not BAIDU_APPID or not BAIDU_KEY:
        raise RuntimeError("未配置 BAIDU_APPID / BAIDU_KEY")
    salt = random.randint(32768, 65536)
    sign = hashlib.md5(f"{BAIDU_APPID}{text}{salt}{BAIDU_KEY}".encode("utf-8")).hexdigest()
    resp = requests.get(
        "https://fanyi-api.baidu.com/api/trans/vip/translate",
        params={
            "q": text,
            "from": "en",
            "to": "zh",
            "appid": BAIDU_APPID,
            "salt": salt,
            "sign": sign,
        },
        timeout=15,
    )
    data = resp.json()
    if "trans_result" not in data:
        raise RuntimeError(f"百度翻译错误: {data.get('error_msg', data)}")
    return "".join(r["dst"] for r in data["trans_result"])

def translate(text: str) -> str:
    if TRANSLATE_PROVIDER == "baidu":
        return _translate_baidu(text)
    if not DEEPSEEK_API_KEY:
        raise RuntimeError("未配置 DEEPSEEK_API_KEY，请设置环境变量")
    return _translate_deepseek(text)

def annotate(text: str) -> dict:
    """逐句翻译，返回中文注释文本与句子列表。"""
    sentences = split_sentences(text)
    items = []
    for s in sentences:
        try:
            zh = translate(s)
        except Exception as e:  # 单句失败不中断
            zh = f"[翻译失败: {e}]"
        items.append({"en": s, "zh": zh})
    annotated = "".join(f"{it['en']}\n{it['zh']}\n\n" for it in items)
    return {"sentences": items, "annotated": annotated}

# ---------------- API ----------------

class TextRequest(BaseModel):
    text: str

@app.get("/health")
def health():
    return {"status": "ok", "provider": TRANSLATE_PROVIDER}

@app.post("/api/translate")
def api_translate(req: TextRequest, authorization: Optional[str] = None):
    _check_auth(authorization)
    if not req.text.strip():
        return {"translated": ""}
    return {"translated": translate(req.text.strip())}

@app.post("/api/annotate")
def api_annotate(req: TextRequest, authorization: Optional[str] = None):
    _check_auth(authorization)
    if not req.text.strip():
        return {"sentences": [], "annotated": ""}
    return annotate(req.text.strip())

# ---------------- 可选：自托管 Whisper 转写（类似 WhisperLiveKit） ----------------

def _get_whisper_model():
    global _whisper_model
    if _whisper_model is None:
        from faster_whisper import WhisperModel  # 可选依赖
        _whisper_model = WhisperModel(
            WHISPER_MODEL_NAME, device="auto", compute_type="int8"
        )
    return _whisper_model

@app.post("/api/transcribe")
def api_transcribe(file: UploadFile = File(...), authorization: Optional[str] = None):
    _check_auth(authorization)
    model = _get_whisper_model()
    with tempfile.NamedTemporaryFile(suffix=".m4a", delete=True) as tmp:
        tmp.write(file.file.read())
        tmp.flush()
        segments, info = model.transcribe(tmp.name, beam_size=5)
        text = "".join(seg.text for seg in segments).strip()
    return {"text": text, "language": getattr(info, "language", None)}
