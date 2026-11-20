; Set-Tai desktop Windows installer (per-user, no admin needed). Built on Linux with makensis.
Unicode true
SetCompressor /SOLID lzma
SetCompressorDictSize 64
!include "MUI2.nsh"

!ifndef SRC
  !define SRC "image\Settai"
!endif
!ifndef VERSION
  !define VERSION "0.0"
!endif
!ifndef VNUM4
  !define VNUM4 "0.0.0.0"
!endif
!ifndef OUTFILE
  !define OUTFILE "set-tai-desktop-windows-setup.exe"
!endif

Name "Set-Tai"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\Programs\Settai"
InstallDirRegKey HKCU "Software\Settai" "InstallDir"
RequestExecutionLevel user
BrandingText "Set-Tai ${VERSION}"
VIProductVersion "${VNUM4}"
VIAddVersionKey "ProductName" "Set-Tai"
VIAddVersionKey "CompanyName" "COMBO MEALS"
VIAddVersionKey "FileDescription" "Set-Tai installer"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "ProductVersion" "${VERSION}"

!define MUI_ICON "settai.ico"
!define MUI_UNICON "settai.ico"
!define MUI_WELCOMEPAGE_TITLE "Set-Tai - your desktop companion"
!define MUI_WELCOMEPAGE_TEXT "Set-Tai is a cheeky companion who lives on top of your windows: it walks, climbs, hides at the edges and chats with you (local AI via Ollama, or an online AI).$\r$\n$\r$\nInstalls just for you - no administrator rights needed."
!define MUI_FINISHPAGE_RUN "$INSTDIR\Settai.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Start Set-Tai now"
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

Section "Set-Tai (required)" SecMain
  SectionIn RO
  ; stop a running copy so files can be replaced
  nsExec::Exec `powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Process java,javaw -ErrorAction SilentlyContinue | Where-Object { $$_.Path -like '$INSTDIR\runtime\*' } | Stop-Process -Force"`
  SetOutPath "$INSTDIR"
  RMDir /r "$INSTDIR\runtime"
  RMDir /r "$INSTDIR\app"
  File /r "${SRC}\*.*"
  WriteUninstaller "$INSTDIR\Uninstall Settai.exe"
  CreateDirectory "$SMPROGRAMS"
  CreateShortcut "$SMPROGRAMS\Set-Tai.lnk" "$INSTDIR\Settai.exe" "" "$INSTDIR\Settai.exe" 0
  WriteRegStr HKCU "Software\Settai" "InstallDir" "$INSTDIR"
  !define UNINST "Software\Microsoft\Windows\CurrentVersion\Uninstall\Settai"
  WriteRegStr HKCU "${UNINST}" "DisplayName" "Set-Tai"
  WriteRegStr HKCU "${UNINST}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${UNINST}" "Publisher" "COMBO MEALS"
  WriteRegStr HKCU "${UNINST}" "DisplayIcon" "$INSTDIR\Settai.exe"
  WriteRegStr HKCU "${UNINST}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNINST}" "UninstallString" '"$INSTDIR\Uninstall Settai.exe"'
  WriteRegDWORD HKCU "${UNINST}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINST}" "NoRepair" 1
SectionEnd

Section "Desktop shortcut" SecDesk
  CreateShortcut "$DESKTOP\Set-Tai.lnk" "$INSTDIR\Settai.exe" "" "$INSTDIR\Settai.exe" 0
SectionEnd

Section "Start Set-Tai when I sign in" SecRun
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "Settai" '"$INSTDIR\Settai.exe"'
SectionEnd

Section "Uninstall"
  nsExec::Exec `powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Process java,javaw -ErrorAction SilentlyContinue | Where-Object { $$_.Path -like '$INSTDIR\runtime\*' } | Stop-Process -Force"`
  Sleep 800
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "Settai"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Settai"
  DeleteRegKey HKCU "Software\Settai"
  Delete "$SMPROGRAMS\Settai.lnk"
  Delete "$DESKTOP\Settai.lnk"
  Delete "$SMPROGRAMS\Set-Tai.lnk"
  Delete "$DESKTOP\Set-Tai.lnk"
  RMDir /r "$INSTDIR\runtime"
  RMDir /r "$INSTDIR\app"
  Delete "$INSTDIR\Settai.exe"
  Delete "$INSTDIR\*.txt"
  Delete "$INSTDIR\*.cmd"
  Delete "$INSTDIR\Uninstall Settai.exe"
  RMDir "$INSTDIR"
  ; Settings and chat memory in %APPDATA%\Settai are kept (delete that folder to remove them too).
SectionEnd
