#!/bin/zsh
set -e
SDK_DIR="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
exec "$SDK_DIR/emulator/emulator" -avd Medium_Phone \
  -dns-server 1.1.1.1,8.8.8.8 -no-snapshot -no-boot-anim \
  -gpu host -feature -Vulkan -cores 4 -memory 2048
