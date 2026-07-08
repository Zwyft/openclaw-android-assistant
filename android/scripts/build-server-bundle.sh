#!/usr/bin/env bash
set -euo pipefail

echo "=== Building codex-web-local ==="

# Get absolute path of project root
PROJECT_ROOT="$(git rev-parse --show-toplevel)"
ANDROID_DIR="$PROJECT_ROOT/android"
ASSETS_DIR="$ANDROID_DIR/app/src/main/assets/server-bundle"

# Build frontend (Vue) and CLI (Express server)
echo "Building frontend..."
npm run build:frontend

echo "Building CLI server..."
npm run build:cli

# Copy the built artifacts into assets
echo "Copying build artifacts to Android assets..."
rm -rf "$ASSETS_DIR"
mkdir -p "$ASSETS_DIR/dist"
mkdir -p "$ASSETS_DIR/dist-cli"

cp -r "$PROJECT_ROOT/dist/"* "$ASSETS_DIR/dist/"
cp -r "$PROJECT_ROOT/dist-cli/"* "$ASSETS_DIR/dist-cli/"
cp "$PROJECT_ROOT/package.json" "$ASSETS_DIR/package.json"

# Install production dependencies into the bundle
echo "Installing production dependencies for bundle..."
cd "$ASSETS_DIR"
npm install --omit=dev --ignore-scripts 2>/dev/null || true
cd "$PROJECT_ROOT"

echo "=== Server bundle ready ==="
echo "Location: $ASSETS_DIR"
