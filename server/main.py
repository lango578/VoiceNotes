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
import json
import random
import tempfile
import time
import asyncio
from contextlib import suppress
from typing import List, Optional

import requests
from fastapi import FastAPI, File, UploadFile, WebSocket, WebSocketDisconnect
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
WHISPER_DEVICE = os.getenv("WHISPER_DEVICE", "auto")            # auto | cpu | cuda
WHISPER_COMPUTE_TYPE = os.getenv("WHISPER_COMPUTE_TYPE", "int8")  # int8 | float16
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
            WHISPER_MODEL_NAME, device=WHISPER_DEVICE, compute_type=WHISPER_COMPUTE_TYPE
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


# ---------------- 流式 Whisper（WebSocket，边录边出字，类似 WhisperLiveKit） ----------------
#
# 协议：
#   App 连接 ws://<host>:8000/ws/transcribe?lang=zh|en|yue（lang 可省略 → 自动检测）
#   客户端 → 服务端：二进制帧 = 16kHz/16bit/单声道 PCM 原始字节
#   客户端 → 服务端：文本帧  "flush" = 停止并返回最终转写；"close" = 直接断开
#   服务端 → 客户端：JSON 文本帧
#       {"type":"interim","text":"..."}   滚动中间结果（每积累约 3s 新音频）
#       {"type":"final","text":"...","language":"en"}   最终完整转写
#       {"type":"error","message":"..."}

STREAM_INTERVAL_SEC = 3.0       # 每隔这么多秒的"新音频"做一次中间转写
STREAM_WINDOW_SEC = 15.0        # 中间转写只看最近这么多秒（滚动窗口）
STREAM_MAX_SEC = 300.0          # 缓冲上限（5 分钟），防止长录音内存无限增长
_STREAM_BYTES_PER_SEC = 32000   # 16kHz × 16bit / 8 = 32000 B/s

def _pcm_to_float(raw_pcm: bytes):
    import numpy as np
    return np.frombuffer(raw_pcm, dtype=np.int16).astype(np.float32) / 32768.0

def _transcribe_audio(model, raw_pcm: bytes, lang: str = ""):
    """把 PCM 字节交给 Whisper 转写，返回 (text, language)。"""
    audio = _pcm_to_float(raw_pcm)
    if audio.size == 0:
        return "", None
    language = lang or None
    segments, info = model.transcribe(
        audio, language=language, beam_size=1, vad_filter=True,
        condition_on_previous_text=False,
    )
    text = "".join(seg.text for seg in segments).strip()
    return text, (info.language if language is None else language)

@app.websocket("/ws/transcribe")
async def ws_transcribe(websocket: WebSocket, lang: str = ""):
    await websocket.accept()
    auth = websocket.headers.get("authorization", "")
    if AUTH_TOKEN and auth != f"Bearer {AUTH_TOKEN}":
        await websocket.send_text(json.dumps({"type": "error", "message": "AUTH_TOKEN 不匹配"}))
        await websocket.close(code=4001)
        return
    try:
        model = await asyncio.to_thread(_get_whisper_model)
    except Exception as e:
        await websocket.send_text(json.dumps({"type": "error", "message": f"faster-whisper 未安装或加载失败: {e}"}))
        await websocket.close(code=4002)
        return

    buffer = bytearray()
    lock = asyncio.Lock()
    last_len = 0

    async def stream_loop():
        nonlocal last_len
        last_run = time.monotonic()
        try:
            while True:
                await asyncio.sleep(0.5)
                last_len = min(last_len, len(buffer))
                now = time.monotonic()
                if now - last_run >= STREAM_INTERVAL_SEC and len(buffer) > last_len:
                    last_len = len(buffer)
                    last_run = now
                    window = bytes(buffer[-int(STREAM_WINDOW_SEC * _STREAM_BYTES_PER_SEC):])
                    try:
                        async with lock:
                            text, _ = await asyncio.to_thread(_transcribe_audio, model, window, lang)
                    except Exception as e:
                        text = ""
                    if text:
                        try:
                            await websocket.send_text(json.dumps({"type": "interim", "text": text}))
                        except Exception:
                            return
        except asyncio.CancelledError:
            raise
        except Exception:
            pass

    loop_task = asyncio.create_task(stream_loop())
    try:
        while True:
            msg = await websocket.receive()
            if msg["type"] == "websocket.receive_bytes":
                buffer.extend(msg["bytes"])
                max_bytes = int(STREAM_MAX_SEC * _STREAM_BYTES_PER_SEC)
                if len(buffer) > max_bytes + int(STREAM_WINDOW_SEC * _STREAM_BYTES_PER_SEC):
                    del buffer[: len(buffer) - max_bytes]
            elif msg["type"] == "websocket.receive_text":
                txt = msg.get("text", "").strip()
                if txt == "flush":
                    loop_task.cancel()
                    with suppress(asyncio.CancelledError):
                        await loop_task
                    async with lock:
                        final_text, language = await asyncio.to_thread(
                            _transcribe_audio, model, bytes(buffer), lang
                        )
                    try:
                        await websocket.send_text(json.dumps({
                            "type": "final", "text": final_text, "language": language,
                        }))
                    except Exception:
                        pass
                    break
                elif txt == "close":
                    break
    except WebSocketDisconnect:
        pass
    finally:
        loop_task.cancel()
        with suppress(asyncio.CancelledError):
            await loop_task
        with suppress(Exception):
            await websocket.close()
