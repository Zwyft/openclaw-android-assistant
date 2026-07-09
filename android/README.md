# AnyClaw Android Assistant

**AI-powered coding assistant for Android** — Freebuff integrated, Hermes Web UI unlocked.

## Quick Start

### Download APK
1. Visit [GitHub Actions](https://github.com/Zwyft/openclaw-android-assistant/actions)
2. Click latest successful build
3. Download `anyclaw` artifact
4. Install on Android device

### First Launch
- App will download ~500MB environment (first run only)
- Complete OAuth login when prompted
- Start using AI coding agents

## Features

### Freebuff AI (Unlocked)
- ✏️ **Code Editor** — Generate and refactor code
- ⚡ **Terminal** — Execute shell commands
- 📁 **File Explorer** — Browse project files
- 🔍 **Code Review** — Quality and security analysis
- 🔬 **Research** — Documentation lookup

### Hermes Web UI
- Fully unlocked (no paywall)
- Free AI models: GPT-5 Nano, GPT-5 Mini, Claude Sonnet 4
- Unlimited sessions, no rate limits

## Build from Source

```bash
# Prerequisites: JDK 17, Android SDK
git clone https://github.com/Zwyft/openclaw-android-assistant.git
cd openclaw-android-assistant/android
./gradlew :app:assembleDebug

# APK location:
# app/build/outputs/apk/debug/anyclaw.apk
```

## Architecture

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/codex/mobile/
│   │   │   ├── MainActivity.kt
│   │   │   ├── CodexServerManager.kt
│   │   │   ├── AuthCallbackServer.kt
│   │   │   └── CodebuffAgentManager.kt
│   │   └── assets/
│   │       ├── bootstrap/ (Termux Linux env)
│   │       ├── codebuff/ (AI agents)
│   │       ├── freebuff/ (Unlocked config)
│   │       └── web/ (Vue.js UI)
│   └── build.gradle.kts
└── scripts/
    └── download-bootstrap.sh
```

## Troubleshooting

### Login Issues
- **Browser doesn't open**: Check default browser settings
- **Redirect fails**: Use API key fallback option
- **"Not logged in"**: Restart app and try again

### Build Errors
- **Gradle sync failed**: Ensure JDK 17 is installed
- **Bootstrap download fails**: Check network connection
- **Out of space**: Free up 2GB+ storage

### Runtime Issues
- **App crashes on startup**: Clear app data and reinstall
- **Slow performance**: Close background apps
- **Battery drain**: Normal during initial setup

## Technical Details

### Permissions
- `INTERNET` — API calls and OAuth
- `FOREGROUND_SERVICE` — Background Codex server
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — Persistent server
- `WAKE_LOCK` — Keep server running

### Ports Used
- `18923` — Codex web server
- `18924` — CONNECT proxy
- `18925` — OAuth callback server
- `18789` — OpenClaw gateway
- `19001` — OpenClaw control UI

### Storage Requirements
- Initial download: ~500MB
- Full installation: ~2GB
- APK size: ~36MB

## Development

### Debugging
```bash
# View logs
adb logcat | grep -i "codex\|anyclaw"

# Install debug APK
adb install -r app/build/outputs/apk/debug/anyclaw.apk

# Launch app
adb shell am start -n com.codex.mobile/.MainActivity
```

### Modifying Web UI
```bash
# Web UI sources
android/app/src/main/assets/web/

# After changes, rebuild APK
./gradlew :app:assembleDebug
```

### Adding AI Agents
1. Add agent definition to `assets/codebuff/agents/`
2. Update `agent-launcher.js` registry
3. Rebuild APK

## License

See root `LICENSE` file. This project combines multiple open source components.

## Acknowledgments

- OpenClaw Android Assistant (base project)
- Codebuff/Freebuff (AI integration)
- Termux (Linux environment)
- OpenAI (Codex CLI)
