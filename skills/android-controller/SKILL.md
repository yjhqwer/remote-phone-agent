---
name: android-controller
description: "Control and automate connected Android mobile devices via ADB and scrcpy-mcp. Supports touch (tap, swipe), text input via ADBKeyboard, package launching, screen inspection with visual grounding, and safety checks for payments and sensitive credentials."
---

# Android Controller Skill

Use this skill when interacting with, automating, or inspecting a connected Android device.

## 1. Connection & Session

Before executing actions on the Android device:
1. Ensure the device is connected via ADB:
   ```bash
   adb devices
   ```
   If connected over Tailscale mesh, ensure:
   ```bash
   adb connect <PHONE_TAILSCALE_IP>:5555
   ```
2. Check device power and wakefulness:
   - If screen is off, wake and dismiss keyguard:
     ```bash
     adb shell input keyevent KEYCODE_WAKEUP
     adb shell wm dismiss-keyguard
     ```

## 2. Tool Usage (scrcpy-mcp)

Use the tools provided by the `android-controller` MCP server:
- **`start_session` / `stop_session`**: Start or terminate low-latency scrcpy hardware streaming.
- **`screenshot`**: Capture the current screen state. Inspect the returned image using your multimodal vision capabilities to locate target UI elements.
- **`tap`**: Click on specific screen coordinates `(x, y)`.
- **`swipe`**: Perform scroll gestures (up/down/left/right) or pull-to-refresh.
- **`type_text`**: Type text into the currently active input field.
  - *Chinese / Special character fallback*: If typing fails or garbles text, dispatch via ADBKeyBoard:
    ```bash
    adb shell am broadcast -a ADB_INPUT_TEXT --es msg '<TEXT>'
    ```
- **`press_key`**:
  - Return / Back: `KEYCODE_BACK` (4)
  - Home: `KEYCODE_HOME` (3)
  - App Switch / Recent: `KEYCODE_APP_SWITCH` (187)
  - Enter: `KEYCODE_ENTER` (66)
- **`launch_app`**: Launch an application directly using its package name.

## 3. High-Frequency App Package Dictionary

Prefer direct launch using package names over finding and tapping home screen icons:

| Application | Package Name | Launch Command |
| :--- | :--- | :--- |
| **微信 (WeChat)** | `com.tencent.mm` | `adb shell monkey -p com.tencent.mm -c android.intent.category.LAUNCHER 1` |
| **美团 (Meituan)** | `com.sankuai.meituan` | `adb shell monkey -p com.sankuai.meituan -c android.intent.category.LAUNCHER 1` |
| **高德地图 (Amap)** | `com.autonavi.minimap` | `adb shell monkey -p com.autonavi.minimap -c android.intent.category.LAUNCHER 1` |
| **小红书 (Xiaohongshu)** | `com.xiaohongshu.app` | `adb shell monkey -p com.xiaohongshu.app -c android.intent.category.LAUNCHER 1` |
| **淘宝 (Taobao)** | `com.taobao.taobao` | `adb shell monkey -p com.taobao.taobao -c android.intent.category.LAUNCHER 1` |
| **京东 (JD)** | `com.jingdong.app.mall` | `adb shell monkey -p com.jingdong.app.mall -c android.intent.category.LAUNCHER 1` |
| **系统设置 (Settings)** | `com.android.settings` | `adb shell am start -a android.settings.SETTINGS` |

## 4. Visual Grounding & Coordinate Calculation

- Coordinates returned by visual models are usually normalized between `[0, 1000]`.
- Convert normalized coordinates to device resolution:
  $$\text{Real X} = \frac{\text{Norm X} \times \text{Display Width}}{1000}$$
  $$\text{Real Y} = \frac{\text{Norm Y} \times \text{Display Height}}{1000}$$
- Obtain device display resolution with:
  ```bash
  adb shell wm size
  ```

## 5. Security Guardrails & Human Takeover

STOP and request human takeover in the following scenarios:
1. **Payment & Fund Transfer**: Any page containing "支付", "输入密码", "指纹支付", or financial transaction confirmations.
2. **Authentication**: Captcha sliders, SMS verification code inputs, or biometrics.
3. **Sensitive Permissions**: System permission dialogs for device admin, accessibility, or dangerous file deletion.
