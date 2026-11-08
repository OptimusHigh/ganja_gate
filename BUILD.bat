@echo off
setlocal

rem ── Build GanjaGate NeoForge mod ───────────────────────────────────
rem Uses JDK 25 for NeoForge 26.1.2

set JAVA_HOME=S:\Program Files\Java\jdk-25.0.2

echo [GanjaGate] Building with JDK 25: %JAVA_HOME%
call .\gradlew.bat jar --no-daemon

if %ERRORLEVEL% neq 0 (
    echo [GanjaGate] BUILD FAILED!
    pause
    exit /b 1
)

echo.
echo [GanjaGate] Build successful!
echo [GanjaGate] JAR location: build\libs\ganja-gate-*.jar
echo.

rem ── Copy to FreeSMLauncher client mods folder ──────────────────
set CLIENT_MODS=C:\Users\asuka\AppData\Roaming\FreesmLauncher\instances\GANJ4CRAFT 5\minecraft\mods
if exist "%CLIENT_MODS%" (
    del /Q "%CLIENT_MODS%\ganja-gate-*.jar" 2>nul
    copy /Y build\libs\ganja-gate-*.jar "%CLIENT_MODS%\"
    echo [GanjaGate] Deployed to %CLIENT_MODS%
)

pause
