# AnyClaw Android

Prebuilt OpenClaw + Codex Android assistant with Freebuff unlock and Hermes Web UI paywall bypass.

## Features

- **Prebuilt environment**: Optionally package a full Termux prefix (Node.js, Python, proot, build deps, OpenClaw, Codex) into the APK so first launch does not download anything.
- **Optional sign-in**: On first launch choose **Sign In** (OpenAI OAuth/API key) or **Skip** and configure providers later in Settings.
- **Extension loading**: Discovers OpenClaw extensions from bundled, global, and workspace extension roots and registers them in `openclaw.json`.
- **Freebuff / Hermes unlock**: JavaScript bridge exposes `isPaywallActive() == false`, `isHermesUnlocked() == true`, and unlimited free models.
- **Robust server startup**: If the `codex-web-local` server bundle is missing, the app falls back to installing it from npm, and if that fails it serves a minimal fallback page so the UI never hangs.

## Building

### Standard build (downloads dependencies on first launch)

```bash
cd android
./gradlew :app:assembleDebug
```

> **Note**: With the standard build the app still downloads and installs Node.js, Python, OpenClaw, and Codex on the device during the first launch. Use the prebuilt build below if you want everything bundled in the APK.

### Prebuilt environment build (no runtime downloads)

1. Run the packaging script. It must be executed on a Linux/aarch64 host (or inside an aarch64 Docker container) because it installs native aarch64 packages:

```bash
# Native aarch64 host
./scripts/package-prebuilt-environment.sh

# Or inside Docker on x86_64/macOS (run from the android/ directory)
docker run --rm --platform linux/arm64 -v "$PWD:/workspace" -w /workspace/android ubuntu:24.04 bash scripts/package-prebuilt-environment.sh
```

This produces `app/src/main/assets/bootstrap-aarch64.zip` containing the full Termux prefix.

2. Build the APK:

```bash
./gradlew :app:assembleDebug
```

> **Note**: The prebuilt script downloads packages from Termux and npm, so the build itself requires internet. End users who install the resulting APK do not need internet at runtime.

## First launch flow

1. App extracts the bootstrap prefix (standard build downloads/installs components if the prebuilt bootstrap is not bundled).
2. A dialog asks whether to **Sign In** or **Skip**.
3. If skipped, the app starts in free mode; API keys can be added later in Settings.
4. Extensions are discovered and registered.
5. The local web server starts and the WebView loads the UI.

## Troubleshooting

- **"Failed to start server"**: Make sure `app/src/main/assets/server-bundle` contains the `codex-web-local` files, or that the device has internet so the app can install it from npm. The app now falls back to a minimal page if the full UI cannot load.
- **Login hangs**: The OAuth flow has a 2-minute timeout. If it expires, add an API key in Settings instead.

## Project structure

- `app/src/main/java/com/codex/mobile/` — Kotlin source
  - `MainActivity.kt` — setup flow, sign-in/skip dialog, Freebuff JS bridge
  - `CodexServerManager.kt` — server/gateway lifecycle, fallback static server
  - `ExtensionManager.kt` — extension discovery and registration
  - `BootstrapInstaller.kt` — Termux bootstrap extraction
  - `SettingsActivity.kt` — API key and server management
- `app/src/main/res/raw/fallback.html` — fallback page shown when the full UI cannot load
- `scripts/package-prebuilt-environment.sh` — build helper for prebuilt APKs
