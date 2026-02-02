@echo off
REM Conductor Server Startup Script for Windows Command Prompt
REM Downloads the server JAR if missing and starts it with java
REM
REM Usage:
REM   Interactive:  conductor_server.bat
REM   With args:    conductor_server.bat [PORT] [VERSION]
REM                 conductor_server.bat 9090 3.22.0

set REPO_URL=https://conductor-server.s3.us-east-2.amazonaws.com

REM Defaults
set SERVER_PORT=8080
set CONDUCTOR_VERSION=latest

REM Argument parsing
REM %1 could be port or version (if simple detection needed, but batch is hard)
REM Sticking to positional: %1=PORT, %2=VERSION
REM If %1 is provided, assume it is PORT unless it contains dots or chars?
REM For simplicity in batch, let's keep strict: %1=PORT, %2=VERSION
REM To use default port and custom version: conductor_server.bat default 3.22.0

if not "%~1"=="" (
    if not "%~1"=="default" set SERVER_PORT=%~1
) else if not "%CONDUCTOR_PORT%"=="" (
    set SERVER_PORT=%CONDUCTOR_PORT%
)

if not "%~2"=="" (
    set CONDUCTOR_VERSION=%~2
)

REM Interactive prompts if no args
if "%~1"=="" if "%CONDUCTOR_PORT%"=="" (
    set /p INPUT_PORT="Enter the port for Server [8080]: "
    if not "%INPUT_PORT%"=="" set SERVER_PORT=%INPUT_PORT%
    
    set /p INPUT_VERSION="Enter the version [latest]: "
    if not "%INPUT_VERSION%"=="" set CONDUCTOR_VERSION=%INPUT_VERSION%
)

set JAR_NAME=conductor-server-%CONDUCTOR_VERSION%.jar
set JAR_URL=%REPO_URL%/%JAR_NAME%

REM Use CONDUCTOR_HOME if set, otherwise use current directory
if "%CONDUCTOR_HOME%"=="" set CONDUCTOR_HOME=.

set JAR_PATH=%CONDUCTOR_HOME%\%JAR_NAME%

REM Download JAR if not present
if not exist "%JAR_PATH%" (
    echo Downloading Conductor Server %CONDUCTOR_VERSION%...
    if not exist "%CONDUCTOR_HOME%" mkdir "%CONDUCTOR_HOME%"
    curl -L -o "%JAR_PATH%" "%JAR_URL%"
    if errorlevel 1 (
        echo Failed to download Conductor Server JAR
        exit /b 1
    )
    echo Downloaded to %JAR_PATH%
)

echo Starting Conductor Server %CONDUCTOR_VERSION% on port %SERVER_PORT%...
java -jar "%JAR_PATH%" --server.port=%SERVER_PORT%
