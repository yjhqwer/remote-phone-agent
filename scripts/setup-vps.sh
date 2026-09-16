#!/usr/bin/env bash
# ==============================================================================
# Remote Phone Agent - VPS Setup Script (Debian / Ubuntu)
# ==============================================================================
set -euo pipefail

echo "==> [1/5] Installing base packages (adb, ffmpeg, jq, curl)..."
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q adb ffmpeg jq curl

echo "==> [2/5] Installing scrcpy-mcp globally..."
if command -v npm &>/dev/null; then
    npm install -g scrcpy-mcp
    # Ensure binary symlink in /usr/local/bin
    NODE_BIN_DIR="$(npm bin -g 2>/dev/null || dirname "$(which npm)")"
    if [ -f "$NODE_BIN_DIR/scrcpy-mcp" ]; then
        ln -sf "$NODE_BIN_DIR/scrcpy-mcp" /usr/local/bin/scrcpy-mcp
    fi
else
    echo "Warning: npm not found. Please install Node.js 24+ first."
fi

echo "==> [3/5] Installing Tailscale..."
if ! command -v tailscale &>/dev/null; then
    curl -fsSL https://tailscale.com/install.sh | sh
    systemctl enable --now tailscaled
fi

echo "==> [4/5] Registering android-controller in Antigravity MCP config..."
MCP_CONF="/root/.gemini/config/mcp_config.json"
if [ -f "$MCP_CONF" ]; then
    jq '.mcpServers["android-controller"] = {"command": "/usr/local/bin/scrcpy-mcp", "args": []}' "$MCP_CONF" > /tmp/mcp.json && mv /tmp/mcp.json "$MCP_CONF"
    echo "Registered android-controller in $MCP_CONF."
fi

echo "==> [5/5] Copying Antigravity Skill..."
SKILL_DIR="/root/.gemini/config/skills/android-controller"
mkdir -p "$SKILL_DIR"
if [ -f "$(dirname "$0")/../skills/android-controller/SKILL.md" ]; then
    cp "$(dirname "$0")/../skills/android-controller/SKILL.md" "$SKILL_DIR/SKILL.md"
    echo "Skill installed to $SKILL_DIR."
fi

# Optional daemon restart
if systemctl --user is-active antigravity-cli-daemon.service &>/dev/null; then
    echo "Restarting antigravity daemon..."
    systemctl --user restart antigravity-cli-daemon.service
fi

echo ""
echo "=============================================================================="
echo " Setup complete! Next steps:"
echo " 1. Run 'tailscale up' to join your Tailscale private mesh."
echo " 2. Connect your phone: 'adb connect <PHONE_TAILSCALE_IP>:5555'"
echo "=============================================================================="
