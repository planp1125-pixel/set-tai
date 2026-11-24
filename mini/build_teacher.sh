#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export LD_LIBRARY_PATH="$PWD/mini/vendor/vulkan/usr/lib/x86_64-linux-gnu${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
for package in mini/vendor/*.deb; do dpkg-deb -x "$package" mini/vendor/vulkan; done
cmake -S mini/vendor/llama.cpp -B mini/vendor/llama.cpp/build -G Ninja \
  -DCMAKE_BUILD_TYPE=Release -DGGML_VULKAN=ON -DLLAMA_CURL=OFF \
  -DCMAKE_PREFIX_PATH="$PWD/mini/vendor/vulkan/usr" \
  -DVulkan_GLSLC_EXECUTABLE="$PWD/mini/vendor/vulkan/usr/bin/glslc"
cmake --build mini/vendor/llama.cpp/build --target llama-server llama-cli llama-quantize --parallel 4
mini/vendor/llama.cpp/build/bin/llama-server --list-devices
