# Settai 3.0 for Ubuntu (desktop)

The same Settai as on the phone (same drawing code, same personality and the same [dance]-style action tags), living on your desktop.

## Install

    sudo apt install ./settai_3.0_amd64.deb
    settai &            # or open "Settai" from the app grid

No Java needed: the package bundles its own runtime under /opt/settai.
Uninstall: `sudo apt remove settai`. Your settings live in `~/.config/settai/`.

Without sudo, use the tarball instead: `tar xzf settai-3.0-linux-x64.tar.gz && settai-3.0/bin/Settai`
(run `settai-3.0/install-user.sh` to add it to your launcher).

## Give him a brain (Ollama, local and private)

Ollama is already running on your laptop (`curl localhost:11434/api/version`). It just has no model yet:

    ollama pull gemma3:4b          # ~3.3 GB, the recommended model

Or: right-click Settai → Settings… → **Pull recommended model**, which shows a progress bar. Then Save.
If you want faster replies, smaller models are `gemma3:1b` or `qwen3:1.7b`; pick one with "Or pull another".
Online AI also works: Settings → Online AI → provider + your API key (stored in `~/.config/settai/settai.properties`, mode 0600).

## Using him

* **Double-click** Settai to chat in his cloud bubble. Type, then press Enter to send or Esc to close. The ⤢ button opens the full chat window.
* **Click** his head, belly or feet for different reactions. **Drag** him anywhere. Drop him near an edge and he grabs on.
* **Fling** him at the left, right or bottom screen edge and he hides there behind a small tab. You can drag the tab along the edge. Click it, or pull it out, to bring him back.
* **Right-click** for the menu: chat, dance, wave, climb, hide, settings, quit. The tray icon has the same items, if your panel shows tray icons.
* When the screen locks he curls up asleep. When you unlock, he peeks out, grabs the edge, pulls himself up and does his wake-up routine.
* He watches the laptop:
  * a hot CPU (k10temp) makes him sweat;
  * Do Not Disturb puts a cloth over his mouth;
  * low battery, full RAM, a full disk or a CPU-hogging app each get a comment.
* If an online AI asks to open an app or run a web search, a Yes/No appears in his bubble first. Nothing happens unless you click Yes.
* Settings → "Start Settai when I log in" turns on autostart.
