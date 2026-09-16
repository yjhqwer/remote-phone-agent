# Remote Phone Agent

<p align="center">
  <b>Production-grade Cloud AI Agent Remote Controlling Android Devices via Low-Latency Scrcpy & ADB</b>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License"></a>
  <a href="https://github.com/yjhqwer/remote-phone-agent"><img src="https://img.shields.io/badge/Platform-Android%20%7C%20Linux%20%7C%20Windows-green.svg" alt="Platform"></a>
  <a href="https://modelcontextprotocol.io"><img src="https://img.shields.io/badge/Protocol-MCP-orange.svg" alt="MCP"></a>
  <a href="README.zh-CN.md"><img src="https://img.shields.io/badge/%E6%96%87%E6%A1%A3-%E7%AE%80%E4%BD%93%E4%B8%AD%E6%96%87-blue.svg" alt="Chinese Docs"></a>
</p>

---

## 🌟 Introduction & Motivation

Many mobile AI agent frameworks (e.g., **Operit AI**) attempt to run everything inside the Android device—packing heavy LLM runtimes, PRoot Ubuntu environments, accessibility services, and local terminals into a single mobile app.

In practice, running autonomous agents directly on a mobile phone hits major bottlenecks:
- **Aggressive OEM Battery Optimization**: Background tasks and accessibility services are killed or frozen unexpectedly.
- **Android Sandbox Restrictions**: Severe privilege boundaries, fake Linux environments without real root network control.
- **Thermal Throttling & Battery Drain**: Sustained multimodal inference and screen parsing rapidly overheat mobile chips.

### The Architectural Shift
**Remote Phone Agent** adopts a clean separation of concerns:
- **Cloud/Server (Host Brain)**: Runs 24/7 on an unconstrained Linux VPS (e.g. Google Antigravity, Claude Code, or custom VLM agent) with full Linux root permissions, complete MCP plugin trees, and persistent memory.
- **Android Phone (Edge Target & Trigger)**: Acts strictly as an execution target (receiving hardware-accelerated touch/input commands and streaming frames via scrcpy) and a lightweight wake-up trigger (e.g. sherpa-onnx KWS), keeping phone resources virtually untouched.

---

## 🏗️ Architecture Blueprint

```text
┌─────────────────────────────────────────────────────────────┐
│                    Cloud Server / VPS                       │
│  • AI Brain: Google Antigravity / Claude Code / AutoGLM     │
│  • Agent Skill: android-controller (Package dict & safety)  │
│  • MCP Driver: scrcpy-mcp (Sub-50ms H.264 frame capture)    │
│  • Network: Tailscale Mesh Node / SSH Reverse Tunnel        │
└──────────────────────────────┬──────────────────────────────┘
                               │
               Encrypted P2P WireGuard Tunnel / Reverse SSH
               (Zero exposed public ports to the Internet)
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    Target Android Phone                     │
│  • Port: Persistent TCP 5555 adbd daemon                    │
│  • Display: Hardware MediaCodec screen streaming            │
│  • Input: ADBKeyboard for lossless Chinese/Unicode input    │
│  • Trigger: Ultra-lightweight edge keyword spotting (KWS)   │
└─────────────────────────────────────────────────────────────┘
```

---

## ⚡ Core Features

- **Near-Instant Screen Vision (~33ms)**: Leverages `scrcpy-mcp` binary protocol and hardware video encoding, bypassing slow `adb screencap` (down from 800ms to <50ms).
- **Zero Exposed Public Ports**: Uses **Tailscale** Mesh VPN (fixed `100.x.y.z` private IPs) or reverse SSH tunneling. Your phone's ADB port is never exposed to public internet scanners.
- **Lossless Chinese & Emoji Input**: Bypasses the broken `adb shell input text` with a background IME broadcast receiver (`ADBKeyboard.apk`).
- **High-Frequency App Instant Launch**: Includes an instant-launch dictionary for mainstream applications (WeChat, Amap, Meituan, Xiaohongshu, Taobao, JD) via `am start` / monkey, skipping home screen searching.
- **Safety Interceptor**: Automatically prompts human takeover for payment credentials, SMS verification codes, or dangerous system permissions.

---

## 🚀 Quick Start

### 1. Set Up Cloud Server (VPS)

Run the automated setup script on your Debian / Ubuntu VPS:

```bash
curl -fsSL https://raw.githubusercontent.com/yjhqwer/remote-phone-agent/main/scripts/setup-vps.sh | bash
```

Or manually configure your MCP client (`mcp_config.json`):

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

### 2. Set Up Your Phone

1. **Enable Developer Options & USB Debugging**:
   - Tap `Build Number` 7 times in **Settings → About Phone**.
   - In **Developer Options**, toggle **USB Debugging** and **Wireless Debugging** to ON.
2. **Lock TCP Port to 5555**:
   - Run via PowerShell:
     ```powershell
     .\scripts\setup-phone.ps1 -PhoneIp 192.168.1.2 -PairPort <CURRENT_PORT>
     ```
3. **Install ADBKeyBoard** for Chinese / Unicode input:
   - Download and install [ADBKeyboard.apk](https://github.com/senzhk/ADBKeyBoard/blob/master/ADBKeyboard.apk).
   - Enable `ADB Keyboard` in **Settings → Language & Input**.

### 3. Connect & Control

On your VPS:

```bash
# Connect to your phone
adb connect <PHONE_TAILSCALE_IP>:5555

# Verify connection
adb devices
# Output: <PHONE_TAILSCALE_IP>:5555    device
```

Now, your AI agent can see the screen, tap, swipe, and execute multi-turn phone workflows autonomously!

---

## 📁 Repository Structure

```text
├── README.md                          # English Documentation
├── README.zh-CN.md                    # Chinese Documentation
├── LICENSE                            # MIT License
├── skills/
│   └── android-controller/
│       └── SKILL.md                   # Antigravity / Claude Code Custom Skill
├── scripts/
│   ├── setup-vps.sh                   # Automated Debian/Ubuntu VPS setup
│   ├── setup-phone.ps1                # One-click phone 5555 port locking
│   └── bridge-tunnel.ps1              # Reverse SSH tunnel keepalive
└── config/
    └── mcp_config.example.json        # MCP server template
```

---

## 🛡️ License

Distributed under the **MIT License**. See `LICENSE` for more information.
