#!/bin/bash
#
# package-prebuilt-environment.sh
#
# Builds a pre-installed Termux prefix containing:
#   - proot
#   - Node.js LTS + npm
#   - Python + pip
#   - OpenClaw (npm global install with koffi built)
#   - Codex CLI + native aarch64 binary
#   - build dependencies (git, make, cmake, clang, lld, ...)
#
# The resulting prefix is archived as bootstrap-aarch64.zip and placed in
# android/app/src/main/assets/ so the Android app can extract it on first
# launch without downloading anything.
#
# Requirements:
#   - Native Linux/aarch64 host, OR
#   - Docker with qemu/binfmt support for cross-platform aarch64 emulation
#   - ~5 GB free disk space
#   - Android SDK for the final APK build
#
# Usage:
#   ./android/scripts/package-prebuilt-environment.sh
#
# Docker usage (from repo root):
#   docker run --rm --platform linux/arm64 -v "$PWD:/workspace" -w /workspace/android ubuntu:24.04 bash scripts/package-prebuilt-environment.sh
#

set -euo pipefail

# Guard: this script installs native aarch64 packages.
ARCH="$(uname -m)"
if [ "$ARCH" != "aarch64" ] && [ "$ARCH" != "arm64" ]; then
    echo "ERROR: This script must run on an aarch64 host or inside an aarch64 Docker container." >&2
    echo "Current architecture: $ARCH" >&2
    echo "Try: docker run --rm --platform linux/arm64 -v \"$PWD:/workspace\" -w /workspace/android ubuntu:24.04 bash scripts/package-prebuilt-environment.sh" >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ASSETS_DIR="$ANDROID_DIR/app/src/main/assets"
PREFIX_DIR="$ANDROID_DIR/build/prebuilt-prefix"
STAGING_DIR="$ANDROID_DIR/build/prebuilt-staging"

mkdir -p "$ASSETS_DIR" "$PREFIX_DIR" "$STAGING_DIR"

# ---------------------------------------------------------------------------
# 1. Bootstrap a minimal Termux prefix (or reuse an existing one)
# ---------------------------------------------------------------------------
if [ ! -f "$PREFIX_DIR/bin/sh" ]; then
    echo "[1/6] Downloading Termux bootstrap..."
    # Official Termux bootstrap archives
    TERMUX_BOOTSTRAP_URL="https://packages.termux.dev/bootstrap/bootstrap-aarch64.zip"
    curl -L "$TERMUX_BOOTSTRAP_URL" -o "$STAGING_DIR/bootstrap.zip"
    unzip -o "$STAGING_DIR/bootstrap.zip" -d "$STAGING_DIR/bootstrap"
    cp -a "$STAGING_DIR/bootstrap"/* "$PREFIX_DIR/"
fi

# ---------------------------------------------------------------------------
# 2. Configure apt to use the local prefix
# ---------------------------------------------------------------------------
export PREFIX="$PREFIX_DIR"
export PATH="$PREFIX/bin:$PATH"
export HOME="$PREFIX_DIR/home"
mkdir -p "$HOME"

cat > "$PREFIX/etc/apt/apt.conf" <<EOF
Dir "/";
Dir::State "$PREFIX/var/lib/apt/";
Dir::State::status "$PREFIX/var/lib/dpkg/status";
Dir::Cache "$PREFIX/var/cache/apt/";
Dir::Log "$PREFIX/var/log/apt/";
Dir::Etc "$PREFIX/etc/apt/";
Dir::Etc::SourceList "$PREFIX/etc/apt/sources.list";
Dir::Etc::SourceParts "";
Dir::Bin::dpkg "$PREFIX/bin/dpkg";
Dir::Bin::Methods "$PREFIX/lib/apt/methods/";
Acquire::AllowInsecureRepositories "true";
EOF

cat > "$PREFIX/etc/apt/sources.list" <<EOF
deb http://packages.termux.dev/apt/termux-main stable main
EOF

# ---------------------------------------------------------------------------
# 3. Install packages needed at runtime and build time
# ---------------------------------------------------------------------------
echo "[2/6] Updating package lists..."
apt-get update --allow-insecure-repositories

echo "[3/6] Installing runtime packages (proot, nodejs, python)..."
apt-get install -y --allow-unauthenticated \
    proot libtalloc \
    nodejs-lts npm \
    python python-pip

echo "[4/6] Installing build dependencies..."
apt-get install -y --allow-unauthenticated \
    git make cmake clang binutils lld \
    libllvm libedit libffi ndk-sysroot ndk-multilib libcompiler-rt \
    libarchive libxml2 liblzma libcurl libuv libnghttp2 libnghttp3 \
    rhash jsoncpp

# ---------------------------------------------------------------------------
# 4. Install OpenClaw and Codex CLI
# ---------------------------------------------------------------------------
echo "[5/6] Installing OpenClaw..."
npm install -g --ignore-scripts openclaw@latest

# Build koffi native module
KOFFI_DIR="$PREFIX/lib/node_modules/openclaw/node_modules/koffi"
if [ -f "$KOFFI_DIR/src/cnoke/cnoke.js" ]; then
    export CC=clang CXX=clang++
    cd "$KOFFI_DIR"
    node src/cnoke/cnoke.js -p . -d src/koffi --prebuild
fi

echo "[5/6] Installing Codex CLI..."
npm install -g @openai/codex

# Install native Codex binary manually because npm refuses on Android
CODEX_VERSION="0.104.0"
mkdir -p "$STAGING_DIR/codex-bin"
cd "$STAGING_DIR/codex-bin"
curl -L "https://registry.npmjs.org/@openai/codex/-/codex-${CODEX_VERSION}-linux-arm64.tgz" -o codex-bin.tgz
tar xzf codex-bin.tgz
CODEX_TARGET="$PREFIX/lib/node_modules/@openai/codex-linux-arm64"
mkdir -p "$CODEX_TARGET/vendor/aarch64-unknown-linux-musl/codex"
cp package/vendor/aarch64-unknown-linux-musl/codex/codex "$CODEX_TARGET/vendor/aarch64-unknown-linux-musl/codex/codex"
cp package/package.json "$CODEX_TARGET/package.json"
chmod 700 "$CODEX_TARGET/vendor/aarch64-unknown-linux-musl/codex/codex"

# ---------------------------------------------------------------------------
# 5. Package the prefix into bootstrap-aarch64.zip
# ---------------------------------------------------------------------------
echo "[6/6] Packaging prebuilt environment..."
cd "$PREFIX_DIR"
zip -r "$STAGING_DIR/bootstrap-aarch64.zip" . -x "tmp/*" "var/cache/apt/archives/*"
cp "$STAGING_DIR/bootstrap-aarch64.zip" "$ASSETS_DIR/bootstrap-aarch64.zip"

echo "Done. Prebuilt environment packaged to:"
echo "  $ASSETS_DIR/bootstrap-aarch64.zip"
echo ""
echo "Build the APK with:"
echo "  cd $ANDROID_DIR && ./gradlew :app:assembleDebug"
