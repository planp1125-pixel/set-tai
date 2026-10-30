/* Settai.exe: starts the bundled Java runtime (javaw, no console) on app\settai.jar.
 * Layout:  Settai.exe  app\settai.jar  runtime\bin\javaw.exe
 * "Settai.exe --console" uses java.exe and waits, so logs are visible (for troubleshooting). */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <wchar.h>

int WINAPI wWinMain(HINSTANCE h, HINSTANCE p, LPWSTR args, int show) {
    wchar_t exe[MAX_PATH], dir[MAX_PATH], java[MAX_PATH], jar[MAX_PATH];
    static wchar_t cmd[8192];
    GetModuleFileNameW(NULL, exe, MAX_PATH);
    wcscpy(dir, exe);
    wchar_t *slash = wcsrchr(dir, L'\\'); if (slash) *slash = 0;
    int console = args && wcsstr(args, L"--console") != NULL;
    swprintf(java, MAX_PATH, L"%ls\\runtime\\bin\\%ls", dir, console ? L"java.exe" : L"javaw.exe");
    swprintf(jar, MAX_PATH, L"%ls\\app\\settai.jar", dir);
    if (GetFileAttributesW(java) == INVALID_FILE_ATTRIBUTES || GetFileAttributesW(jar) == INVALID_FILE_ATTRIBUTES) {
        MessageBoxW(NULL, L"Settai's files are missing next to Settai.exe (runtime\\ or app\\).\nPlease unzip/reinstall the whole folder.", L"Settai", MB_ICONERROR);
        return 1;
    }
    swprintf(cmd, 8192,
        L"\"%ls\" -Xms32m -Xmx384m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Dsun.java2d.noddraw=true "
        L"-Dfile.encoding=UTF-8 -Dsettai.exe=\"%ls\" -jar \"%ls\" %ls",
        java, exe, jar, args ? args : L"");
    STARTUPINFOW si; PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof si); si.cb = sizeof si; ZeroMemory(&pi, sizeof pi);
    if (!CreateProcessW(NULL, cmd, NULL, NULL, FALSE, 0, NULL, dir, &si, &pi)) {
        MessageBoxW(NULL, L"Couldn't start Settai's Java runtime.", L"Settai", MB_ICONERROR);
        return 1;
    }
    DWORD code = 0;
    if (console) { WaitForSingleObject(pi.hProcess, INFINITE); GetExitCodeProcess(pi.hProcess, &code); }
    CloseHandle(pi.hThread); CloseHandle(pi.hProcess);
    return (int)code;
}
