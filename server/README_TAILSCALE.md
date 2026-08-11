# VoiceNotes 后端 — Tailscale 异地组网教程

手机不在家里/公司局域网时（用移动流量、在公司、在异地），也能连上家里的自建后端 —— 用 **Tailscale**（基于 WireGuard 的零配置组网）：手机与服务器组建一个私有虚拟局域网（`100.x.y.z`），流量端到端加密，无需公网 IP、无需端口映射。

```
[OPPO 手机 · Tailscale App]  ←──加密隧道──→  [家里 Linux 电脑 · tailscaled]
  Android 13+ / OPPO 手机        WireGuard         只运行 VoiceNotes 后端
```

> 适用后端部署在哪：Linux（推荐，见 [README_LINUX.md](README_LINUX.md)）、Windows、macOS 均可，Tailscale 全平台支持。

---

## 目录

1. [为什么用 Tailscale](#1-为什么用-tailscale)
2. [服务器端：安装并登录](#2-服务器端安装并登录)
3. [手机端：安装并登录同一账号](#3-手机端安装并登录同一账号)
4. [App 设置](#4-app-设置)
5. [安全加固建议](#5-安全加固建议)
6. [自建 Headscale（可选进阶）](#6-自建-headscale可选进阶)
7. [故障排查](#7-故障排查)

---

## 1. 为什么用 Tailscale

| 优点 | 说明 |
|---|---|
| 免公网 IP / 免端口映射 | 不需要路由器开 DMZ，不需要去运营商要公网 IP |
| 端到端加密 | WireGuard，流量不经过第三方明文 |
| 超低延迟 | 走 P2P 直连（NAT 打洞），失败时才走 DERP 中继 |
| 免费额度够用 | 个人 3 台设备、100 台以内设备免费 |
| 全平台 | Android / iOS / Linux / Windows / macOS |

对照其他方案：
- **Nginx + HTTPS 公网部署**：适合有公网 IP/云主机，见 [README_LINUX.md §9](README_LINUX.md)，需要域名+证书。
- **Tailscale**：适合"家里的电脑当服务器"、无公网 IP、不想折腾端口映射的场景。

---

## 2. 服务器端：安装并登录

以 Linux 为例（Windows/macOS 请到 https://tailscale.com/download 下载安装包）：

```bash
# 1) 安装
curl -fsSL https://tailscale.com/install.sh | sh

# 2) 启动并登录（会打印一个 URL，浏览器打开并登录你的账号）
sudo tailscale up

# 3) 拿到虚拟局域网 IP（记下来，这就是手机要填的后端地址 IP）
tailscale ip -4
# 输出示例: 100.64.0.5
```

确认后端本身也正常：

```bash
curl http://127.0.0.1:8000/health        # 后端本地正常
curl http://100.64.0.5:8000/health       # 通过 Tailscale 虚拟网访问（应同样返回 {"status":"ok",...}）
```

> 若后端尚未安装，先按 [README_LINUX.md](README_LINUX.md)（或 Windows/macOS 教程）部署好再继续。

---

## 3. 手机端：安装并登录同一账号

1. OPPO 手机打开应用商店/Play Store，安装 **Tailscale**。
2. 打开 App → **Sign in**，用**与服务器同一个账号**登录（Google / Microsoft / GitHub 均可，两边一致即可）。
3. 允许 Tailscale 建立 VPN（OPPO 会提示"连接 VPN？"→ 允许）。
4. 登录后 App 主界面会显示你的虚拟 IP（`100.x.y.z`），以及同网络里的其他设备（服务器）——看到服务器那台设备就是连接成功。

> 手机开着 Tailscale VPN 的同时，正常使用网络不受影响；它只在后台维护加密隧道。

---

## 4. App 设置

打开 **Voice Notes → 设置**：

- **识别引擎** = 自建后端识别（Whisper）→ 后端地址填：
  ```
  http://100.64.0.5:8000        ← 换成第 2 步 tailscale ip -4 看到的地址
  ```
- **注释模式** = 后端翻译 → 后端地址同上
- 若服务器配了 `AUTH_TOKEN`，**后端 Token** 填相同值。

然后正常录音即可：人在公司/外地，声音实时送回家里的服务器转写（流式 /ws/transcribe 边录边出字），录音停止后得到最终文本。

---

## 5. 安全加固建议

Tailscale 让任意登录了你账号的设备都能访问你的服务器。建议：

1. **后端加 Token 鉴权**（强烈推荐）
   ```bash
   # 服务器 .env 里设置：
   AUTH_TOKEN=一串随机长字符串
   # 重启后端后，App 设置里的"后端 Token"填同样的值
   # 不填 Token 的请求一律被拒
   ```
   生成随机 Token：`openssl rand -hex 24`

2. **用 ACL 限制设备互通**（Tailscale 管理后台 https://login.tailscale.com/admin/acls）
   默认 ACL 允许所有设备互通。可改为只允许手机 → 服务器 8000 端口：
   ```json
   {
     "acls": [
       {
         "action": "accept",
         "src": ["100.64.0.0/10"],
         "dst": ["100.64.0.5:8000"]
       }
     ]
   }
   ```
   （把 `100.64.0.5` 换成服务器地址；想更严格就把 `src` 限定为手机的具体 IP。）

3. **用 tailscale serve 上 HTTPS**（可选）
   ```bash
   sudo tailscale serve --bg --https=443 http://127.0.0.1:8000
   ```
   之后 App 后端地址可填 `https://<机器名>.<tailnet>.ts.net:443`（自动签 Let's Encrypt 证书）。

4. **别把 AUTH_TOKEN 写进 git**；`.env` 仅服务器本机可见（chmod 600）。

---

## 6. 自建 Headscale（可选进阶）

不想依赖 Tailscale 官方协调服务器、想完全自主可控时，可以自建 **Headscale**（Tailscale 的开源控制平面）：

```bash
# 在有公网 IP 的云主机上部署 headscale（可用 Docker）：
#   https://headscale.net/stable/installation/
# 客户端改为连接自建服务器：
tailscale up --login-server https://你的域名
```

手机 Tailscale App 也支持自定义登录服务器（`--login-server`），用法相同。适合追求完全私有化的场景；普通个人使用官方 Tailscale 免费版即可。

---

## 7. 故障排查

| 现象 | 处理 |
|---|---|
| 手机 App 里看不到服务器设备 | 确认两台设备用**同一个账号**登录；`tailscale status` 在服务器上查看设备列表 |
| `curl http://100.x:8000/health` 超时 | ① 后端是否监听 `0.0.0.0`；② 服务器防火墙是否放行 8000（Linux 见 [README_LINUX.md §8](README_LINUX.md)）；③ `tailscale ping <手机IP>` 测试隧道 |
| 能 ping 通但 App 报错 | 后端 Token 不匹配；或后端没装 faster-whisper（流式/转写会报错，但翻译可用） |
| 流量慢 | 默认走 P2P；若走了 DERP 中继会慢，`tailscale ping` 观察；网络允许时自动切回直连 |
| 换设备后 IP 变化 | 虚拟 IP 通常固定；用 `tailscale ip -4` 重新确认，并在 App 里更新 |
| 不想让某设备访问 | 在 Tailscale 管理后台将该设备移除，或用 ACL 限制（§5） |

---
