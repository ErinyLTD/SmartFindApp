#!/bin/bash
set -euo pipefail

# ============================================================
# Android SDK Installation Script for Linux (Arch Linux)
# ============================================================

# --- Configuration ---
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
CMDLINE_TOOLS_VERSION="11076708"  # latest as of 2025
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"

# SDK packages to install (edit as needed)
COMPILE_SDK="34"
BUILD_TOOLS="34.0.0"
NDK_VERSION="26.1.10909125"
INSTALL_NDK=false

# --- Colors ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()   { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# --- Prerequisites ---
log "Installing prerequisites..."
sudo pacman -Syu --noconfirm --needed jdk17-openjdk wget unzip curl

JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
log "Using JAVA_HOME=$JAVA_HOME"

# --- Download Command Line Tools ---
log "Creating SDK directory at $ANDROID_SDK_ROOT"
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"

TEMP_ZIP=$(mktemp /tmp/android-cmdline-tools-XXXXXX.zip)

log "Downloading Android Command Line Tools..."
wget -q --show-progress -O "$TEMP_ZIP" "$CMDLINE_TOOLS_URL"

log "Extracting..."
unzip -q -o "$TEMP_ZIP" -d "$ANDROID_SDK_ROOT/cmdline-tools"

# The zip extracts to "cmdline-tools/cmdline-tools" — move to "cmdline-tools/latest"
rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"

rm -f "$TEMP_ZIP"

# --- Set up paths ---
export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

# --- Accept Licenses ---
log "Accepting SDK licenses..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true

# --- Install SDK Packages ---
log "Installing SDK packages..."

PACKAGES=(
    "platform-tools"
    "platforms;android-${COMPILE_SDK}"
    "build-tools;${BUILD_TOOLS}"
    "extras;google;m2repository"
    "extras;android;m2repository"
)

if [ "$INSTALL_NDK" = true ]; then
    PACKAGES+=("ndk;${NDK_VERSION}")
fi

sdkmanager "${PACKAGES[@]}"

# --- Write Environment Variables ---
log "Setting up environment variables..."

SHELL_RC=""
if [ -f "$HOME/.bashrc" ]; then
    SHELL_RC="$HOME/.bashrc"
elif [ -f "$HOME/.zshrc" ]; then
    SHELL_RC="$HOME/.zshrc"
fi

ENV_BLOCK="
# --- Android SDK ---
export ANDROID_SDK_ROOT=\"$ANDROID_SDK_ROOT\"
export ANDROID_HOME=\"$ANDROID_SDK_ROOT\"
export PATH=\"\$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:\$ANDROID_SDK_ROOT/platform-tools:\$PATH\"
"

if [ -n "$SHELL_RC" ]; then
    if ! grep -q "ANDROID_SDK_ROOT" "$SHELL_RC" 2>/dev/null; then
        echo "$ENV_BLOCK" >> "$SHELL_RC"
        log "Added environment variables to $SHELL_RC"
    else
        warn "Android SDK environment variables already exist in $SHELL_RC — skipping"
    fi
else
    warn "Could not find .bashrc or .zshrc. Add these to your shell profile manually:"
    echo "$ENV_BLOCK"
fi

# --- Verify Installation ---
log "Verifying installation..."
echo ""
echo "============================================"
echo " Android SDK installed successfully"
echo "============================================"
echo ""
echo " ANDROID_SDK_ROOT : $ANDROID_SDK_ROOT"
echo " JAVA_HOME        : $JAVA_HOME"
echo " sdkmanager       : $(which sdkmanager)"
echo " adb              : $(which adb 2>/dev/null || echo 'restart shell to access')"
echo ""
echo " Installed packages:"
sdkmanager --list_installed 2>/dev/null || sdkmanager --list | head -30
echo ""
echo "============================================"
echo " Run 'source $SHELL_RC' or open a new terminal"
echo " to use the SDK."
echo "============================================"
