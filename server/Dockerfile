# VoiceNotes 后端服务镜像（CPU 可用；GPU 需 nvidia-container-runtime）
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

ENV TRANSLATE_PROVIDER=deepseek \
    DEEPSEEK_API_KEY="" \
    DEEPSEEK_BASE_URL=https://api.deepseek.com \
    DEEPSEEK_MODEL=deepseek-chat \
    BAIDU_APPID="" \
    BAIDU_KEY="" \
    AUTH_TOKEN=""

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
