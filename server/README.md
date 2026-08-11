# VoiceNotes 后端服务（自建翻译 + 可选 Whisper 转写）

为"语音笔记"App 提供 **英文→中文逐句注释** 和可选的 **自托管 Whisper 转写**（思路参考 [WhisperLiveKit](https://github.com/QuentinFuxa/WhisperLiveKit)，本项目聚焦你的使用场景，依赖更轻）。

> 🐧 **Linux 部署请直接看 [README_LINUX.md](README_LINUX.md)**：一键安装脚本、systemd 开机自启、GPU(CUDA) 适配、防火墙/Nginx、故障排查。

## 功能

| 接口 | 方法 | 说明 |
|---|---|---|
| `/health` | GET | 健康检查，返回当前翻译后端 |
| `/api/translate` | POST | `{"text":"..."}` → `{"translated":"中文"}` 整段翻译 |
| `/api/annotate` | POST | `{"text":"英文段落"}` → `{"sentences":[{"en","zh"},...],"annotated":"英文句\n中文\n\n..."}` 逐句注释 |
| `/api/transcribe` | POST | 上传音频文件 → `{"text":"...","language":"en"}` 自托管 Whisper 转写（可选） |

可选鉴权：设置 `AUTH_TOKEN` 后，所有接口需带请求头 `Authorization: Bearer <token>`。

## 翻译后端（环境变量 `TRANSLATE_PROVIDER`）

### 1. DeepSeek / OpenAI 兼容 API（默认，推荐）
```bash
export TRANSLATE_PROVIDER=deepseek
export DEEPSEEK_API_KEY=sk-xxxx                # 必填，DeepSeek 开放平台：https://platform.deepseek.com
export DEEPSEEK_BASE_URL=https://api.deepseek.com   # 也可换成 Kimi/通义/智谱 等 OpenAI 兼容地址
export DEEPSEEK_MODEL=deepseek-chat
```

### 2. 百度翻译 API（有免费额度）
```bash
export TRANSLATE_PROVIDER=baidu
export BAIDU_APPID=你的AppID
export BAIDU_KEY=你的密钥
# 百度翻译开放平台：https://fanyi-api.baidu.com
```

## 运行

### 方式一：本机 Python
```bash
cd server
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

### 方式二：Docker
```bash
docker build -t voicenotes-server .
docker run -d -p 8000:8000 \
  -e DEEPSEEK_API_KEY=sk-xxxx \
  -e AUTH_TOKEN=可选token \
  --name voicenotes-server voicenotes-server
```

### 方式三：可选 Whisper 转写（类似 WhisperLiveKit）
```bash
# 需要系统安装 ffmpeg（faster-whisper 解码音频用）
# Ubuntu/Debian:  sudo apt install ffmpeg    macOS:  brew install ffmpeg
pip install faster-whisper   # 取消 requirements.txt 里的注释，或单独安装
export WHISPER_MODEL=small   # tiny/base/small/medium/large-v3
```
启动后手机 App 即可把它作为"自建转写后端"使用（需要与 App 同局域网或公网可达）。

## 测试
```bash
curl http://127.0.0.1:8000/health
curl -X POST http://127.0.0.1:8000/api/annotate \
  -H "Content-Type: application/json" \
  -d '{"text":"Hello world. This is a voice note."}'
```
