# VoiceNotes 后端 — Linux 系统操作教程与适配

本文档针对 **Linux 服务器/电脑** 部署 VoiceNotes 自建后端（`server/`），包含：系统与配置需求、一键/手动安装、Whisper CPU/GPU 适配、systemd 开机自启、防火墙、Nginx 反代 HTTPS、运维与故障排查。

适用场景：
- 家里/公司局域网内一台常开的 Linux 机器，供 OPPO 手机 App 用「**自建后端识别（Whisper）**」和「**后端翻译（英文→中文注释）**」；
- 或一台有公网 IP / 云主机的 Linux 服务器，手机在外网也能访问。

---

## 目录

1. [系统需求与配置需求](#1-系统需求与配置需求)
2. [部署架构总览](#2-部署架构总览)
3. [方式一：一键安装脚本（推荐）](#3-方式一一键安装脚本推荐)
4. [方式二：手动部署](#4-方式二手动部署)
5. [配置需求详解（.env 全变量表）](#5-配置需求详解env-全变量表)
6. [Whisper 适配：CPU 与 NVIDIA GPU](#6-whisper-适配cpu-与-nvidia-gpu)
7. [systemd 服务管理（开机自启/日志）](#7-systemd-服务管理开机自启日志)
8. [防火墙与端口](#8-防火墙与端口)
9. [公网部署：Nginx 反向代理 + HTTPS](#9-公网部署nginx-反向代理--https)
10. [Docker 部署（含 GPU）](#10-docker-部署含-gpu)
11. [与手机 App 联调](#11-与手机-app-联调)
12. [运维与更新](#12-运维与更新)
13. [常见故障排查（Linux）](#13-常见故障排查linux)
14. [卸载](#14-卸载)

---

## 1. 系统需求与配置需求

### 1.1 软件需求

| 软件 | 要求 | 说明 |
|---|---|---|
| Linux 发行版 | Ubuntu 22.04/24.04、Debian 12、CentOS/Rocky/Alma 9、Fedora | 其他发行版请自行安装等价包 |
| Python | 3.10 ~ 3.12 | 3.13 也可用，但建议 3.11/3.12 最稳 |
| ffmpeg | 任意新版 | Whisper 解码 `.m4a` 必需（PyAV 依赖其共享库） |
| systemd | 自带 | 用于开机自启（绝大多数发行版默认） |
| Docker（可选） | 24+ | 方式三部署用 |

### 1.2 硬件需求（按功能分档）

**仅翻译 + 逐句注释（不开 Whisper）** —— 最低配置，任何老电脑/树莓派/云主机均可：

| 项目 | 最低 |
|---|---|
| CPU | 1 核即可 |
| 内存 | 256 MB 空闲 |
| 磁盘 | 200 MB |
| 网络 | 能访问 DeepSeek API / 百度翻译 API |

**开启 Whisper 转写（faster-whisper，int8 量化）** —— 按模型大小：

| 模型 | 参数量 | 内存/显存占用 | CPU 实时率* | 适用 |
|---|---|---|---|---|
| `tiny` | 39M | ~1 GB | 快于实时 | 低配机器应急 |
| `base` | 74M | ~1.5 GB | ≈ 实时 | 4 核 CPU |
| `small` | 244M | ~2.5 GB | 略慢于实时 | 8 核 CPU / 入门 GPU |
| `medium` | 769M | ~5 GB | 明显慢于实时 | 12 GB+ 显存 GPU |
| `large-v3` | 1550M | ~10 GB | 不建议 CPU | 24 GB 显存 GPU |

\* 实时率 = 1 分钟录音的处理耗时。CPU 上建议 `small` 以下；有 NVIDIA GPU 时全部可用且远快于实时。

**GPU（可选，强烈建议用于 medium 以上）**：
- NVIDIA 显卡，驱动版本 **≥ 535**（推荐 545+），支持 CUDA 12.x
- 显存：`small` ≥ 2 GB，`medium` ≥ 8 GB，`large-v3` ≥ 12 GB（float16）

> ⚠️ 模型首次启动会从 HuggingFace 下载（`small` 约 460 MB），请确保机器能访问 `huggingface.co`；无法直连时参考 §6.3 配置镜像。

### 1.3 端口与网络

| 项目 | 值 |
|---|---|
| 默认端口 | `8000`（可在 .env / install.sh 中改 `PORT`） |
| 内网访问 | 手机与服务器同一局域网，服务器 `0.0.0.0` 监听 |
| 公网访问 | 建议配 Nginx 反代 + HTTPS（见 §9） |

---

## 2. 部署架构总览

```
[OPPO 手机 · VoiceNotes App]
        │  HTTP（同局域网或公网 HTTPS）
        ▼
[Nginx（可选）: 80/443 → 8000]
        ▼
[systemd: voicenotes-server.service]
    /opt/voicenotes-server/
      ├── main.py                服务端代码
      ├── requirements.txt       依赖清单
      ├── venv/                  Python 虚拟环境
      ├── .env                   配置（密钥，权限 600）
      └── .cache/                Whisper 模型缓存
```

- 服务以**专用系统用户 `voicenotes`** 运行（非 root，更安全）；
- 接口鉴权可选：设置 `AUTH_TOKEN` 后，请求需带 `Authorization: Bearer <token>`；
- 仅对局域网开放时也可不加 Token，由路由器隔离。

---

## 3. 方式一：一键安装脚本（推荐）

把仓库里 `server/` 目录（含 `linux/` 子目录）拷贝到你的 Linux 机器，然后：

```bash
# 1. 先设定配置（不 export 则用默认值）
---

## 4. 方式二：手动部署

```bash
# 1. 安装系统依赖（Ubuntu/Debian 示例）
sudo apt update
sudo apt install -y python3 python3-venv python3-pip ffmpeg curl

# 2. 把 server 目录拷到 /opt
sudo mkdir -p /opt/voicenotes-server
sudo cp server/main.py server/requirements.txt /opt/voicenotes-server/
cd /opt/voicenotes-server

# 3. 虚拟环境 + 依赖
sudo python3 -m venv venv
sudo ./venv/bin/pip install --upgrade pip
sudo ./venv/bin/pip install -r requirements.txt
# 启用 Whisper 时：
sudo ./venv/bin/pip install faster-whisper

# 4. 写配置
sudo tee /opt/voicenotes-server/.env >/dev/null <<'EOF'
TRANSLATE_PROVIDER=deepseek
DEEPSEEK_API_KEY=sk-xxxx
AUTH_TOKEN=
WHISPER_MODEL=small
WHISPER_DEVICE=auto
WHISPER_COMPUTE_TYPE=int8
EOF

# 5. 先手动起一次验证
sudo /opt/voicenotes-server/venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000
# Ctrl+C 停止后，按 §7 装 systemd 服务开机自启
```

---

## 5. 配置需求详解（.env 全变量表）

配置文件：`/opt/voicenotes-server/.env`（一行 `KEY=VALUE`，`#` 开头为注释，**不要加引号**）。改完必须 `sudo systemctl restart voicenotes-server`。

| 变量 | 必填 | 默认值 | 说明 |
|---|---|---|---|
| `TRANSLATE_PROVIDER` | 是 | `deepseek` | `deepseek`（OpenAI 兼容）或 `baidu` |
| `DEEPSEEK_API_KEY` | ① | 空 | DeepSeek 密钥，https://platform.deepseek.com 获取 |
| `DEEPSEEK_BASE_URL` | 否 | `https://api.deepseek.com` | 可换 Kimi/通义/智谱 等 OpenAI 兼容地址 |
| `DEEPSEEK_MODEL` | 否 | `deepseek-chat` | 模型名 |
| `BAIDU_APPID` | ① | 空 | 百度翻译开放平台 AppID |
| `BAIDU_KEY` | ① | 空 | 百度翻译密钥 |
| `AUTH_TOKEN` | 否 | 空 | 接口鉴权；设置后 App 需填相同「后端 Token」 |
| `WHISPER_MODEL` | 否 | `small` | `tiny/base/small/medium/large-v3` |
| `WHISPER_DEVICE` | 否 | `auto` | `auto`（自动）`cpu`（强制 CPU）`cuda`（NVIDIA GPU） |
| `WHISPER_COMPUTE_TYPE` | 否 | `int8` | CPU 用 `int8`；CUDA 建议 `float16`（速度快、省显存） |

① `TRANSLATE_PROVIDER=deepseek` 时必须填 `DEEPSEEK_API_KEY`；`=baidu` 时必须填 `BAIDU_APPID` 和 `BAIDU_KEY`。

> 不配置翻译密钥时，翻译/注释接口会报错，但 `/api/transcribe`（Whisper 转写）仍可用。
> 只有 `AUTH_TOKEN` 非空时，才要求请求带 `Authorization: Bearer <token>`。

---

## 6. Whisper 适配：CPU 与 NVIDIA GPU

`main.py` 已适配两种设备：`WHISPER_DEVICE`（`auto/cpu/cuda`）+ `WHISPER_COMPUTE_TYPE`（`int8/float16`）。模型只加载一次，常驻内存，后续转写不重复加载。

### 6.1 CPU 部署（默认）

```bash
# .env
WHISPER_MODEL=small
WHISPER_DEVICE=cpu          # 或 auto
WHISPER_COMPUTE_TYPE=int8   # CPU 推荐 int8
```

`int8` 量化把内存占用和耗时都降到约 1/4。模型文件默认缓存到 `/opt/voicenotes-server/.cache`（systemd 单元已设 `HF_HOME`）。

### 6.2 NVIDIA GPU 部署（CUDA）

1. 确认驱动与 CUDA 可用：
   ```bash
   nvidia-smi          # 显示驱动版本与显存即 OK
   ```
   > faster-whisper 的 CUDA 推理由 ctranslate2 提供，需 **CUDA 12.x 运行时库**。安装脚本不会装 CUDA；请用系统包管理器装：
   > ```bash
   > # Ubuntu 官方 CUDA 仓库（以 12.4 为例）
   > sudo apt install -y cuda-toolkit-12-4
   > # 或直接用 pip 装 ctranslate2 的 cu12 版（推荐，无需系统 CUDA 工具链）
   > sudo /opt/voicenotes-server/venv/bin/pip install "ctranslate2>=4.4.0"
   > # 注意：pip 版 ctranslate2 自带 CUDA 12 动态库，需要系统 libcuda.so.1（由 NVIDIA 驱动提供）→ 装驱动即满足
   > ```
2. 修改 `.env`：
   ```bash
   WHISPER_DEVICE=cuda
   WHISPER_COMPUTE_TYPE=float16
   ```
3. 重启并验证：
   ```bash
   sudo systemctl restart voicenotes-server
   journalctl -u voicenotes-server -n 20 | grep -i whisper
   ```

> 常见坑：`device="cuda"` 但驱动太旧 / 无 libcuda.so.1 → 报错 `CUDA driver version is insufficient`。用 `nvidia-smi` 对照驱动版本，升级到 ≥535 即可。内存不足时降级 `WHISPER_MODEL`。
---

## 7. systemd 服务管理（开机自启/日志）

安装脚本已写入 `/etc/systemd/system/voicenotes-server.service`。常用命令：

```bash
sudo systemctl status voicenotes-server       # 状态（active/running/failed）
sudo systemctl restart voicenotes-server      # 重启（改 .env 后必用）
sudo systemctl stop voicenotes-server         # 停止
sudo systemctl start voicenotes-server        # 启动
sudo systemctl enable voicenotes-server       # 开机自启（安装脚本已默认开启）
sudo systemctl disable voicenotes-server      # 取消开机自启

journalctl -u voicenotes-server -f            # 实时跟踪日志
journalctl -u voicenotes-server -n 100        # 最近 100 行
journalctl -u voicenotes-server --since "10 min ago"
```

服务单元要点（`/etc/systemd/system/voicenotes-server.service`）：

| 配置项 | 值 | 作用 |
|---|---|---|
| `User` | `voicenotes` | 以非 root 系统用户运行 |
| `EnvironmentFile` | `/opt/voicenotes-server/.env` | 读取密钥配置 |
| `Environment=HF_HOME` | `/opt/voicenotes-server/.cache` | 模型缓存到应用目录 |
| `ExecStart` | venv 内 `uvicorn main:app --host 0.0.0.0 --port 8000 --workers 1` | 单进程（Whisper 模型常驻） |
| `Restart=on-failure` | — | 崩溃自动拉起 |

> ⚠️ `--workers 1` 必须保持为 1：多 worker 会各自加载一份 Whisper 模型，占用成倍内存。

---

## 8. 防火墙与端口

手机需要能连服务器的 `8000` 端口。如果服务器开了防火墙：

```bash
# Ubuntu/Debian (ufw)
sudo ufw allow 8000/tcp
sudo ufw status

# CentOS/Rocky/Alma (firewalld)
sudo firewall-cmd --permanent --add-port=8000/tcp
sudo firewall-cmd --reload
```

验证本机与局域网可达：

```bash
curl http://127.0.0.1:8000/health                    # 本机
curl http://<服务器局域网IP>:8000/health              # 局域网其他机器
```

> 局域网 IP 查询：`ip -4 addr show | grep inet`（例 `192.168.1.20`）。若手机仍连不上，检查路由器的「AP 隔离/客户端隔离」是否关闭。

---

## 9. 公网部署：Nginx 反向代理 + HTTPS

仅局域网用可跳过本节。有公网 IP / 云主机时，用 Nginx 把 443（HTTPS）转发到 8000，手机填 `https://你的域名` 即可。

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
```

Nginx 配置 `/etc/nginx/sites-available/voicenotes`：

```nginx
server {
    listen 80;
    server_name voice.example.com;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 300s;      # Whisper 转写可能较久
        client_max_body_size 100m;    # 允许上传录音文件
    }
}
```

启用并申请证书：

```bash
sudo ln -s /etc/nginx/sites-available/voicenotes /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d voice.example.com   # 自动配 HTTPS
```

之后 App 设置：后端地址填 `https://voice.example.com`，并让 App 信任该 HTTPS 证书（标准证书即信任）。

> 反代路径保持根路径即可，App 用的是绝对路径 `/api/transcribe`、`/api/annotate` 等。

---

## 10. Docker 部署（含 GPU）

### 10.1 CPU

```bash
cd server
docker build -t voicenotes-server .
docker run -d --name voicenotes-server --restart=unless-stopped \
  -p 8000:8000 \
  -e DEEPSEEK_API_KEY=sk-xxxx \
  -e AUTH_TOKEN=可选 \
  voicenotes-server
```

> 该镜像默认 **不含** faster-whisper（`requirements.txt` 中已注释）。需要转写时在镜像内执行：
> ```bash
> docker exec voicenotes-server pip install faster-whisper
> # 并确认镜像内已有 ffmpeg（Dockerfile 已装）
> ```

### 10.2 NVIDIA GPU（float16 加速）

```bash
# 主机先装 NVIDIA 驱动 + nvidia-container-toolkit
sudo apt install -y nvidia-container-toolkit
sudo systemctl restart docker
---

## 12. 运维与更新

### 12.1 常规检查

```bash
systemctl status voicenotes-server          # 服务健康
journalctl -u voicenotes-server -f          # 实时日志
free -h                                     # 内存（Whisper 常驻）
du -sh /opt/voicenotes-server/.cache        # 模型缓存大小
```

### 12.2 更新服务端代码

```bash
# 从仓库拉取新 main.py / requirements.txt 后：
sudo cp server/main.py server/requirements.txt /opt/voicenotes-server/
sudo /opt/voicenotes-server/venv/bin/pip install -r /opt/voicenotes-server/requirements.txt
sudo systemctl restart voicenotes-server
```

### 12.3 更换 Whisper 模型

改 `.env` 中 `WHISPER_MODEL` → `systemctl restart`。首次切换会重新下载，旧模型不会自动删除，可手动清理 `/opt/voicenotes-server/.cache/huggingface`。

---

## 13. 常见故障排查（Linux）

### 服务起不来
```bash
sudo systemctl status voicenotes-server
journalctl -u voicenotes-server -n 50
```
- `ModuleNotFoundError: fastapi` → venv 装依赖失败，重跑 `pip install -r requirements.txt`
- `Permission denied` → 检查 `/opt/voicenotes-server` 属主：`sudo chown -R voicenotes:voicenotes /opt/voicenotes-server`
- `Address already in use` → 8000 被占，`ss -ltnp | grep 8000` 找出进程，改 `PORT` 或停掉旧进程

### Whisper 相关
- `ffmpeg not found` / `libavcodec not found` → 安装 ffmpeg：`sudo apt install -y ffmpeg`，然后重启服务
- `CUDA driver version is insufficient` → 驱动过旧，升级到 ≥535；或改回 `WHISPER_DEVICE=cpu`
- 内存/显存不足被系统杀（OOM Kill）→ `dmesg | tail` 看是否 `killed process`；换更小的模型
- 首次转写卡住 → 正在下载模型（看日志）；国内网络按 §6.3 设 `HF_ENDPOINT`

### 翻译失败
- `/api/annotate` 报「未配置 DEEPSEEK_API_KEY」→ 检查 `.env` 的键名与值（**不要加引号**）
- DeepSeek 返回 401 → 密钥无效或未充值
- 用百度时报错 → 确认 `TRANSLATE_PROVIDER=baidu` 且 AppID/密钥正确

### 手机连不上
- 确认服务器监听 `0.0.0.0`（日志或 `ss -ltnp | grep 8000` 应显示 `0.0.0.0:8000`）
- 手机和服务器不在同一网段 / 路由器开了 AP 隔离 → 关掉或改公网部署
- 防火墙未放行 → 见 §8
- 公网 HTTPS 证书不被信任 → 用 certbot 签标准证书

### 日志快速定位
```bash
journalctl -u voicenotes-server -n 50 --no-pager   # 最近 50 行
```

---

## 14. 卸载

```bash
# 在当初拷贝的 server/linux/ 目录下执行：
sudo bash uninstall.sh
# 或直接手动执行（等价）：
sudo systemctl stop voicenotes-server && sudo systemctl disable voicenotes-server
sudo rm -f /etc/systemd/system/voicenotes-server.service && sudo systemctl daemon-reload
sudo userdel voicenotes
sudo rm -rf /opt/voicenotes-server   # 会删除 Whisper 模型缓存
```

---

## 附录：快速自检清单

```bash
# 一键自检（逐条应通过）
systemctl is-active voicenotes-server && echo OK1   # 服务运行中
curl -s http://127.0.0.1:8000/health | grep -q '"status":"ok"' && echo OK2   # 健康检查
curl -s -X POST http://127.0.0.1:8000/api/annotate \
  -H "Content-Type: application/json" \
  -d '{"text":"Hello world. This is a voice note."}' | head -c 200 && echo OK3  # 翻译可用
which ffmpeg && echo OK4                              # ffmpeg 已装
ss -ltnp | grep ':8000' && echo OK5                   # 监听 0.0.0.0:8000
```


cd server
docker build -f Dockerfile.gpu -t voicenotes-server-gpu .
docker run -d --name voicenotes-server-gpu --restart=unless-stopped \
  --gpus all -p 8000:8000 --env-file .env voicenotes-server-gpu
```

`Dockerfile.gpu` 基于 `nvidia/cuda:12.4.0-runtime`，自带 faster-whisper，默认 `WHISPER_DEVICE=cuda`、`WHISPER_COMPUTE_TYPE=float16`。

---

## 11. 与手机 App 联调

1. 手机与服务器同一 Wi-Fi（公网部署则无需）。
2. App → 设置：
   - **识别引擎** = 自建后端识别（Whisper）→ 后端地址 `http://<服务器IP>:8000`（公网填 `https://域名`）
   - **注释模式** = 后端翻译 → 后端地址同上
   - 若设了 `AUTH_TOKEN`，后端 Token 填相同值
3. 录音 → 停止 → App 上传 `.m4a` → 服务器转写 → 返回文本与注释。

服务端日志会看到请求（用 `journalctl -u voicenotes-server -f` 观察）。首次转写会先下载模型，需等待 1~3 分钟。


### 6.3 无法直连 HuggingFace（国内服务器）

```bash
# 方案 A：环境变量指向镜像站
sudo tee -a /opt/voicenotes-server/.env >/dev/null <<'EOF'
HF_ENDPOINT=https://hf-mirror.com
EOF

# 方案 B：用 systemd 覆盖文件持久化（推荐）
sudo mkdir -p /etc/systemd/system/voicenotes-server.service.d
sudo tee /etc/systemd/system/voicenotes-server.service.d/hf.conf >/dev/null <<'EOF'
[Service]
Environment=HF_ENDPOINT=https://hf-mirror.com
EOF
sudo systemctl daemon-reload && sudo systemctl restart voicenotes-server
```

模型下载完成后会常驻内存，之后不再联网。

export DEEPSEEK_API_KEY=sk-xxxxxxxx          # DeepSeek 密钥（必填，或用百度）
export ENABLE_WHISPER=yes                     # 开启 Whisper 转写
export WHISPER_MODEL=small
export AUTH_TOKEN=my-secret-token             # 可选

# 2. 运行安装（需要 root/sudo）
sudo bash install.sh
```

脚本自动完成 6 步：
1. 检测包管理器（apt/dnf/yum）并安装 `python3 / python3-venv / python3-pip / ffmpeg / curl`；
2. 创建目录 `/opt/voicenotes-server` 和专用系统用户 `voicenotes`；
3. 创建 Python 虚拟环境并安装依赖（`ENABLE_WHISPER=yes` 时额外装 faster-whisper）；
4. 生成 `.env`（若已存在则保留，`chmod 600`）；
5. 安装并启用 systemd 服务 `voicenotes-server.service`；
6. 自检：打印服务状态并 `curl /health`。

安装成功后立即验证：

```bash
curl http://127.0.0.1:8000/health
# {"status":"ok","provider":"deepseek"}
```

> 若之后修改了 `/opt/voicenotes-server/.env`，执行 `sudo systemctl restart voicenotes-server`。
