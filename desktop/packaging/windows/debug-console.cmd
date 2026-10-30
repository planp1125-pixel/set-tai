@echo off
rem Runs Settai with a console so you can see its log.
set SETTAI_DEBUG=1
"%~dp0runtime\bin\java.exe" -Xmx384m -Dsettai.exe="%~dp0Settai.exe" -jar "%~dp0app\settai.jar" %*
pause
