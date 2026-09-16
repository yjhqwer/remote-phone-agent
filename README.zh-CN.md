# Remote Phone Agent (云端智能体远程控机套件)

<p align="center">
  <b>基于云端大模型 Agent + Scrcpy 低延迟硬件流 + ADB 远程接管 Android 真实手机的工业级解决方案</b>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License"></a>
  <a href="https://github.com/yjhqwer/remote-phone-agent"><img src="https://img.shields.io/badge/%E5%B9%B3%E5%8F%B0-Android%20%7C%20Linux%20%7C%20Windows-green.svg" alt="Platform"></a>
  <a href="https://modelcontextprotocol.io"><img src="https://img.shields.io/badge/%E5%8D%8F%E8%AE%AE-MCP-orange.svg" alt="MCP"></a>
  <a href="README.md"><img src="https://img.shields.io/badge/Docs-English-blue.svg" alt="English Docs"></a>
</p>

---

## 🌟 为什么做这个项目？

目前移动端的 AI Agent（如 **Operit AI**）大多尝试在手机内部跑完全部逻辑——将重型大模型推理、PRoot 模拟的 Ubuntu 终端、无障碍辅助服务、本地语音唤醒全塞进一个 Android App。

这种模式在真机使用中必然会撞上下列不可逾越的物理限制：
- **系统杀后台与墓碑机制**：国产 Android 系统激进的省电策略会无情斩杀后台常驻服务；
- **沙箱与权限限制**：PRoot 是基于 `ptrace` 的假 Linux，没有真正独立的内核与网络管理权限；
- **发热、降频与电量焦虑**：持续的多模态推理与图像解析会让手机在几分钟内剧烈发热并严重掉电。

### 核心解耦思想
**Remote Phone Agent** 采用**“云端宿主大脑 + 真实手机靶机”**的职责解耦架构：
- **云端 VPS（宿主大脑）**：24/7 常驻运行在无限制的 Linux 云服务器（如 Google Antigravity、Claude Code、AutoGLM 等），拥有真实的 Linux Root 权限、不受限的外部工具链（MCP、爬虫、长期记忆库）与稳定算力。
- **Android 手机（端侧靶机 & 轻量触发）**：只作为纯粹的**执行靶机**（通过 scrcpy 硬件推流画面，接收点击指令）与**轻量唤醒端**（如 sherpa-onnx 极低功耗关键词唤醒），手机几乎零负载。

---

## 🏗️ 整体架构图

```text
┌─────────────────────────────────────────────────────────────┐
│                    云端服务器 / VPS (宿主大脑)               │
│  • 核心大脑: Google Antigravity / Claude Code / AutoGLM     │
│  • 控机技能: android-controller (内置高频包名字典与防黑屏安全)│
│  • 驱动底座: scrcpy-mcp (<50ms 硬件视频流与触控协议)        │
│  • 安全组网: Tailscale 虚拟 Mesh 私网 / SSH 反向加密隧道     │
└──────────────────────────────┬──────────────────────────────┘
                               │
               加密 P2P WireGuard 虚拟内网 / 反向隧道
               (公网零暴露 5555 端口，无惧黑客扫描)
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    真实安卓手机 (受控靶机)                   │
│  • 监听端口: 固化 TCP 5555 adbd 守护进程                    │
│  • 画面采集: 手机 MediaCodec 硬件加速推流                   │
│  • 文字输入: ADBKeyboard 广播接收器 (无损中文/Emoji)        │
│  • 交互触发: 离线超低功耗关键词检测 (KWS 派单)              │
└─────────────────────────────────────────────────────────────┘
```

---

## ⚡ 核心亮点

- **毫秒级视觉感知（~33ms）**：基于 `scrcpy` 二进制协议直接从硬件抓取视频帧，彻底告别传统 `adb screencap` 每次截屏耗时 800ms ~ 1.5s 的卡顿。
- **零公网端口暴露**：使用 **Tailscale** 加密内网（分配固定 `100.x.y.z` 私网 IP）或 SSH 反向端口隧道，绝不把 ADB 端口裸露在公网上。
- **无损中文与特殊符号输入**：完美绕过 `adb shell input text` 无法输入中文的硬伤，通过 `ADBKeyboard` 广播秒级注入任意中文与 Emoji。
- **高频 App 秒级直达**：内置微信、美团、小红书、高德地图、淘宝、京东等官方 Package 秒开字典，跳过桌面翻页找图标步骤。
- **安全拦截防线**：内置敏感操作拦截机制，当识别到支付密码、短信验证码、危险系统权限弹窗时，主动暂停并转交人工接管。

---

## 🚀 快速上手

### 第一步：配置云端 VPS

在你的 Debian / Ubuntu VPS 上执行一键部署脚本：

```bash
curl -fsSL https://raw.githubusercontent.com/yjhqwer/remote-phone-agent/main/scripts/setup-vps.sh | bash
```

或者手动在你的 MCP 配置文件（`mcp_config.json`）中挂载：

```json
{
  "mcpServers": {
    "android-controller": {
      "command": "/usr/local/bin/scrcpy-mcp",
      "args": []
    }
  }
}
```

### 第二步：配置你的手机

1. **开启开发者选项与无线调试**：
   - 进入【设置】➔【关于手机】➔ 连续点击【版本号】7次激活开发者选项；
   - 在【开发者选项】中开启 **【USB 调试】** 与 **【无线调试】**。
2. **固化 5555 调试端口**：
   - 在电脑端运行辅助脚本（或手机连电脑执行一次 `adb tcpip 5555`）：
     ```powershell
     .\scripts\setup-phone.ps1 -PhoneIp 192.168.1.2 -PairPort <无线调试当前显示的端口>
     ```
3. **安装中文输入法支持**：
   - 下载并安装 [ADBKeyboard.apk](https://github.com/senzhk/ADBKeyBoard/blob/master/ADBKeyboard.apk)。
   - 在系统【语言与输入法】中启用 `ADB Keyboard`。

### 第三步：连接与控制

在 VPS 上执行连接指令：

```bash
# 连接手机
adb connect <你的手机Tailscale_IP>:5555

# 检查连接状态
adb devices
# 成功输出：<你的手机Tailscale_IP>:5555    device
```

此时云端 Antigravity 智能体已经拥有了控制手机的“眼睛和手”，可以通过自然语言下发如“打开微信”、“点亮屏幕并返回桌面”等任务！

---

## 📁 项目目录结构

```text
├── README.md                          # 英文说明文档
├── README.zh-CN.md                    # 中文说明文档
├── LICENSE                            # MIT 开源协议
├── skills/
│   └── android-controller/
│       └── SKILL.md                   # Antigravity 专属控机技能定义
├── scripts/
│   ├── setup-vps.sh                   # Debian/Ubuntu VPS 自动化部署脚本
│   ├── setup-phone.ps1                # 局域网内一键切换手机 5555 端口脚本
│   └── bridge-tunnel.ps1              # SSH 反向加密隧道保活脚本
└── config/
    └── mcp_config.example.json        # MCP 挂载配置文件示例
```

---

## 🛡️ 开源协议

本项目采用 **MIT License** 授权，详情见 `LICENSE` 文件。
