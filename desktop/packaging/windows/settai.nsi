; Settai 3.0 Windows installer (per-user, no admin needed). Built on Linux with makensis.
Unicode true
SetCompressor /SOLID lzma
SetCompressorDictSize 64
!include "MUI2.nsh"

!ifndef SRC
  !define SRC "image\Settai"
!endif
!ifndef OUTFILE
  !define OUTFILE "settai-3.0-windows-setup.exe"
!endif

Name "Settai"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\Programs\Settai"
InstallDirRegKey HKCU "Software\Settai" "InstallDir"
RequestExecutionLevel user
BrandingText "Settai 3.0"
VIProductVersion "3.0.0.0"
VIAddVersionKey "ProductName" "Settai"
VIAddVersionKey "CompanyName" "COMBO MEALS"
VIAddVersionKey "FileDescription" "Settai installer"
VIAddVersionKey "FileVersion" "3.0"
VIAddVersionKey "ProductVersion" "3.0"

!define MUI_ICON "settai.ico"
!define MUI_UNICON "settai.ico"
!define MUI_WELCOMEPAGE_TITLE "Settai - your desktop monkey"
!define MUI_WELCOMEPAGE_TEXT "Settai is a cheeky monkey who lives on top of your windows: he walks, climbs, hides at the edges and chats with you (local AI via Ollama, or an online AI).$\r$\n$\r$\nInstalls just for you - no administrator rights needed."
!define MUI_FINISHPAGE_RUN "$INSTDIR\Settai.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Start Settai now"
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

Section "Settai (required)" SecMain
  SectionIn RO
  ; stop a running copy so files can be replaced
  nsExec::Exec `powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Process java,javaw -ErrorAction SilentlyContinue | Where-Object { $$_.Path -like '$INSTDIR\runtime\*' } | Stop-Process -Force"`
  SetOutPath "$INSTDIR"
  RMDir /r "$INSTDIR\runtime"
  RMDir /r "$INSTDIR\app"
  File /r "${SRC}\*.*"
  WriteUninstaller "$INSTDIR\Uninstall Settai.exe"
  CreateDirectory "$SMPROGRAMS"
  CreateShortcut "$SMPROGRAMS\Settai.lnk" "$INSTDIR\Settai.exe" "" "$INSTDIR\Settai.exe" 0
  WriteRegStr HKCU "Software\Settai" "InstallDir" "$INSTDIR"
  !define UNINST "Software\Microsoft\Windows\CurrentVersion\Uninstall\Settai"
  WriteRegStr HKCU "${UNINST}" "DisplayName" "Settai"
  WriteRegStr HKCU "${UNINST}" "DisplayVersion" "3.0"
  WriteRegStr HKCU "${UNINST}" "Publisher" "COMBO MEALS"
  WriteRegStr HKCU "${UNINST}" "DisplayIcon" "$INSTDIR\Settai.exe"
  WriteRegStr HKCU "${UNINST}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNINST}" "UninstallString" '"$INSTDIR\Uninstall Settai.exe"'
  WriteRegDWORD HKCU "${UNINST}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINST}" "NoRepair" 1
SectionEnd

Section "Desktop shortcut" SecDesk
  CreateShortcut "$DESKTOP\Settai.lnk" "$INSTDIR\Settai.exe" "" "$INSTDIR\Settai.exe" 0
SectionEnd

Section "Start Settai when I sign in" SecRun
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
  RMDir /r "$INSTDIR\runtime"
  RMDir /r "$INSTDIR\app"
  Delete "$INSTDIR\Settai.exe"
  Delete "$INSTDIR\*.txt"
  Delete "$INSTDIR\*.cmd"
  Delete "$INSTDIR\Uninstall Settai.exe"
  RMDir "$INSTDIR"
  ; Settings and chat memory in %APPDATA%\Settai are kept (delete that folder to remove them too).
SectionEnd
