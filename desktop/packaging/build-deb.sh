#!/usr/bin/env bash
# Builds Settai desktop: self-contained app image (bundled Java runtime) -> .deb + .tar.gz
# Needs a FULL JDK 21+ (with jpackage and AWT/X11 libs, e.g. Temurin) in $JDK, and dpkg-deb.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="$(cd "$HERE/../.." && pwd)"
JDK="${JDK:-/workspace/tools/jdk-21.0.12.1+1}"
OUT="${OUT:-$ROOT/desktop/build/dist}"
VER=3.0
rm -rf "$OUT"; mkdir -p "$OUT"
(cd "$ROOT" && ./gradlew -q :desktop:fatJar)
JAR="$ROOT/desktop/build/libs/settai-desktop-all.jar"

# Icons rendered from the same monkey renderer
for s in 48 64 128 256 512; do "$JDK/bin/java" -jar "$JAR" --render-icon "$OUT/icons/${s}.png" $s; done

# Trimmed runtime
"$JDK/bin/jlink" --add-modules java.base,java.desktop,java.logging,jdk.crypto.ec,jdk.unsupported \
  --strip-java-debug-attributes --strip-native-debug-symbols=objcopy=/usr/bin/objcopy --no-man-pages --no-header-files --compress=zip-0 --output "$OUT/runtime"

mkdir -p "$OUT/input"; cp "$JAR" "$OUT/input/settai.jar"
"$JDK/bin/jpackage" --type app-image --name Settai --app-version $VER --vendor "COMBO MEALS" \
  --input "$OUT/input" --main-jar settai.jar --main-class com.combo.settai.desktop.MainKt \
  --runtime-image "$OUT/runtime" --icon "$OUT/icons/256.png" --dest "$OUT/image" \
  --java-options "-Xms32m -Xmx384m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Dsun.java2d.xrender=true -Dawt.useSystemAAFontSettings=on -Dsun.awt.noerasebackground=true --add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"

# ---- .deb ----
PKG="$OUT/deb"; mkdir -p "$PKG/DEBIAN" "$PKG/opt/settai" "$PKG/usr/bin" "$PKG/usr/share/applications"
cp -a "$OUT/image/Settai/." "$PKG/opt/settai/"
ln -s /opt/settai/bin/Settai "$PKG/usr/bin/settai"
cp "$HERE/settai.desktop" "$PKG/usr/share/applications/settai.desktop"
for s in 48 64 128 256 512; do mkdir -p "$PKG/usr/share/icons/hicolor/${s}x${s}/apps"; cp "$OUT/icons/${s}.png" "$PKG/usr/share/icons/hicolor/${s}x${s}/apps/settai.png"; done
mkdir -p "$PKG/usr/share/doc/settai"; cp "$HERE/README-desktop.md" "$PKG/usr/share/doc/settai/README.md"
SIZE=$(du -sk "$PKG" --exclude=DEBIAN | cut -f1)
cat > "$PKG/DEBIAN/control" <<CTL
Package: settai
Version: $VER
Section: games
Priority: optional
Architecture: amd64
Maintainer: COMBO MEALS <noreply@example.com>
Installed-Size: $SIZE
Depends: libc6, libx11-6, libxext6, libxrender1, libxtst6, libxi6, libfreetype6, libfontconfig1, zlib1g
Recommends: libglib2.0-bin
Suggests: ollama
Homepage: https://ollama.com
Description: Settai - cheeky animated monkey desktop companion
 Settai walks along the bottom of your screen, climbs the edges, hangs from
 the top, hides behind the screen edge and chats with you in a cloud bubble.
 His brain is a local Ollama model (private, offline) or an online AI with
 your own API key. Bundles its own Java runtime.
CTL
cat > "$PKG/DEBIAN/postinst" <<'PI'
#!/bin/sh
set -e
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database -q /usr/share/applications || true
command -v gtk-update-icon-cache >/dev/null 2>&1 && gtk-update-icon-cache -q -f /usr/share/icons/hicolor || true
exit 0
PI
cp "$PKG/DEBIAN/postinst" "$PKG/DEBIAN/postrm"
chmod 755 "$PKG/DEBIAN/postinst" "$PKG/DEBIAN/postrm"
find "$PKG" -type d -exec chmod 755 {} +
dpkg-deb --root-owner-group -Zxz -z9 --build "$PKG" "$OUT/settai_${VER}_amd64.deb"

# ---- .tar.gz (no root needed: extract and run settai-3.0/bin/Settai) ----
T="$OUT/tar/settai-$VER"; mkdir -p "$T"
cp -a "$OUT/image/Settai/." "$T/"
cp "$HERE/README-desktop.md" "$T/README.md"
cp "$OUT/icons/256.png" "$T/settai.png"
cat > "$T/install-user.sh" <<'IU'
#!/bin/sh
# Adds Settai to your app launcher (current user only, no sudo).
D="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$HOME/.local/share/applications" "$HOME/.local/share/icons/hicolor/256x256/apps"
cp "$D/settai.png" "$HOME/.local/share/icons/hicolor/256x256/apps/settai.png"
sed "s#/opt/settai/bin/Settai#$D/bin/Settai#" > "$HOME/.local/share/applications/settai.desktop" <<DE
[Desktop Entry]
Type=Application
Name=Settai
Comment=A cheeky animated monkey that lives on your desktop and chats with you
Exec=/opt/settai/bin/Settai
Icon=settai
Terminal=false
Categories=Utility;Amusement;
StartupWMClass=Settai
DE
echo "Settai added to your launcher. Run: $D/bin/Settai"
IU
chmod +x "$T/install-user.sh"
tar -C "$OUT/tar" -czf "$OUT/settai-${VER}-linux-x64.tar.gz" "settai-$VER"
ls -la "$OUT"/*.deb "$OUT"/*.tar.gz
