Settai 3.0 for Windows 10/11 (64-bit)
=====================================

Install
  Installer: run settai-v3-windows-setup.exe. It installs just for you (no admin) into
    %LOCALAPPDATA%\Programs\Settai and adds a Start-menu entry (optional: desktop icon, start at sign-in).
  Portable zip: unzip the whole folder anywhere (e.g. Documents\Settai) and double-click Settai.exe.
  Windows SmartScreen may say "Windows protected your PC" because the app isn't code-signed:
    click "More info" -> "Run anyway".

Use
  Drag him around; double-click him to chat in his bubble; right-click for the menu
  (chat, dance, wave, climb, hide at the edge, settings, quit). Tray icon (^ area of the taskbar) too.
  Fling him at a screen edge to hide him; click the little tab to bring him back.
  He falls asleep when you lock the PC (Win+L) and does his wake-up entrance when you come back.

Local AI (Ollama)
  1. Install Ollama for Windows from https://ollama.com/download (it runs in the tray, at http://localhost:11434).
  2. In Settai: right-click -> Settings -> Local AI -> "Pull recommended model (gemma3:4b)"
     (about 3.3 GB), or in a terminal:  ollama pull gemma3:4b
  3. Pick the model, press "Test brain", then Save.
  Online AI instead: Settings -> Online AI, choose a provider and paste your API key.

Files
  Settings, keys and chat memory: %APPDATA%\Settai\
  Troubleshooting logs: run "Settai (debug console).cmd" in the install folder.

Notes
  Most Windows PCs don't let normal apps read the CPU temperature, so his sweating is estimated
  from CPU load (Settings says which one your PC uses). Focus Assist isn't detected.
