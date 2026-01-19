@echo off
setlocal enabledelayedexpansion

:: Configuration
set "CONDUCTOR_DIR=%USERPROFILE%\.conductor"
set "JAR_PATH=%CONDUCTOR_DIR%\conductor-server-lite.jar"
set "REPO=conductor-oss/conductor"
set "MIN_JAVA_VERSION=21"

:: Create installation directory
if not exist "%CONDUCTOR_DIR%" mkdir "%CONDUCTOR_DIR%"

echo [Conductor Installer] Starting installation...

:: 1. Check for Java
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [Conductor Installer] ERROR: Java not found.
    echo Please install Java %MIN_JAVA_VERSION% or higher from:
    echo https://adoptium.net/temurin/releases/
    exit /b 1
)

:: Check Java version
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VER=%%v
    set JAVA_VER=!JAVA_VER:"=!
    for /f "tokens=1 delims=." %%a in ("!JAVA_VER!") do set JAVA_MAJOR=%%a
)

if !JAVA_MAJOR! LSS %MIN_JAVA_VERSION% (
    echo [Conductor Installer] ERROR: Java !JAVA_MAJOR! found, but Java %MIN_JAVA_VERSION%+ required.
    echo Please install Java %MIN_JAVA_VERSION% or higher from:
    echo https://adoptium.net/temurin/releases/
    exit /b 1
)

echo [Conductor Installer] Found Java !JAVA_MAJOR!

:: 2. Download Conductor JAR
echo [Conductor Installer] Downloading Conductor Server Lite...

:: Get latest release info
curl -s https://api.github.com/repos/%REPO%/releases/latest > "%TEMP%\release.json"

:: Extract download URL (using findstr as a simple approach)
for /f "tokens=*" %%i in ('findstr /C:"browser_download_url" "%TEMP%\release.json" ^| findstr /C:"standalone.jar"') do (
    set "LINE=%%i"
    for /f "tokens=2 delims=: " %%j in ("!LINE!") do (
        set "DOWNLOAD_URL=%%j"
        set "DOWNLOAD_URL=!DOWNLOAD_URL:",=!"
    )
)

if "!DOWNLOAD_URL!"=="" (
    echo [Conductor Installer] ERROR: Could not find JAR in latest release.
    exit /b 1
)

curl -L "!DOWNLOAD_URL!" -o "%JAR_PATH%"
echo [Conductor Installer] Downloaded to %JAR_PATH%

:: 3. Create Launch Script
set "LAUNCHER=%CONDUCTOR_DIR%\start-conductor.cmd"
(
    echo @echo off
    echo java -jar "%JAR_PATH%" %%*
) > "%LAUNCHER%"

echo [Conductor Installer] Installation complete!
echo.
echo To start Conductor, run:
echo   %LAUNCHER%
echo.
echo Or add it to your PATH to run from anywhere:
echo   set PATH=%%PATH%%;%CONDUCTOR_DIR%

:: 4. Optional: Run immediately
if "%1"=="--run" (
    call "%LAUNCHER%"
)
