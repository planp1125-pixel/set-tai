#!/usr/bin/env bash
# Cross-builds Settai for Windows x64 on Linux:
#   app\settai.jar (with OSHI/JNA) + jlink'd Windows runtime (from Windows Temurin jmods)
#   + Settai.exe (mingw-w64 launcher) -> portable zip + NSIS installer.
# Needs: a Linux JDK 21 ($JDK) whose version matches the Windows JDK ($WINJDK), x86_64-w64-mingw32-gcc/windres, makensis, python3+PIL.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(cd "$HERE/../.." && pwd)"
JDK="${JDK:-/workspace/tools/jdk-21.0.12.1+1}"
WINJDK="${WINJDK:-/workspace/tools/win/jdk-21.0.12.1+1}"
OUT="${OUT:-$ROOT/desktop/build/win}"
W="$HERE/windows"
rm -rf "$OUT"; mkdir -p "$OUT/image/Settai/app"
(cd "$ROOT" && ./gradlew -q :desktop:fatJar :desktop:windowsJar)
cp "$ROOT/desktop/build/libs/settai-desktop-windows.jar" "$OUT/image/Settai/app/settai.jar"

# Icon (same renderer as the app) -> multi-size .ico
"$JDK/bin/java" -jar "$ROOT/desktop/build/libs/settai-desktop-all.jar" --render-icon "$OUT/icon256.png" 256
python3 -c "
from PIL import Image; im=Image.open('$OUT/icon256.png').convert('RGBA')
im.save('$OUT/settai.ico', sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])"

# Launcher exe
cp "$W/launcher.rc" "$OUT/"
(cd "$OUT" && x86_64-w64-mingw32-windres launcher.rc -O coff -o launcher.res)
x86_64-w64-mingw32-gcc -O2 -s -municode -mwindows -o "$OUT/image/Settai/Settai.exe" "$W/launcher.c" "$OUT/launcher.res"

# Windows runtime via jlink on Linux with the Windows jmods
"$JDK/bin/jlink" --module-path "$WINJDK/jmods" \
  --add-modules java.base,java.desktop,java.logging,java.management,jdk.crypto.ec,jdk.unsupported,jdk.crypto.mscapi \
  --strip-debug --no-man-pages --no-header-files --compress=zip-0 --output "$OUT/image/Settai/runtime"
rm -f "$OUT/image/Settai/runtime/lib/jvm.lib"; rm -f "$OUT/image/Settai/runtime/bin/"{jrunscript,keytool,kinit,klist,ktab,rmiregistry,jfr}.exe 2>/dev/null || true

cp "$W/README-windows.txt" "$OUT/image/Settai/README.txt"
cp "$W/debug-console.cmd" "$OUT/image/Settai/Settai (debug console).cmd"
sed -i 's/$/\r/' "$OUT/image/Settai/README.txt" "$OUT/image/Settai/Settai (debug console).cmd"

# Portable zip
python3 - "$OUT" <<'PY'
import os, sys, zipfile
out = sys.argv[1]; base = os.path.join(out, "image")
with zipfile.ZipFile(os.path.join(out, "settai-3.0-windows-portable.zip"), "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for d, _, fs in os.walk(os.path.join(base, "Settai")):
        for f in fs:
            p = os.path.join(d, f); z.write(p, os.path.relpath(p, base))
PY

# Installer
cp "$W/settai.nsi" "$OUT/"
(cd "$OUT" && makensis -V2 -DSRC="image\\Settai" -DOUTFILE="settai-3.0-windows-setup.exe" settai.nsi)
ls -l "$OUT"/*.exe "$OUT"/*.zip
