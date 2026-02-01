@echo off
REM Conductor Server Startup Script for Windows Command Prompt
REM Downloads the server JAR if missing and starts it with java
REM
REM Usage:
REM   Interactive:  conductor_server.bat
REM   With port:    conductor_server.bat 9090

set JAR_URL=https://oss-releases.s3.us-east-1.amazonaws.com/conductor-server-latest.jar
set JAR_NAME=conductor-server-latest.jar

REM Use CONDUCTOR_HOME if set, otherwise use current directory
if "%CONDUCTOR_HOME%"=="" set CONDUCTOR_HOME=.

set JAR_PATH=%CONDUCTOR_HOME%\%JAR_NAME%

REM Download JAR if not present
if not exist "%JAR_PATH%" (
    echo Downloading Conductor Server...
    if not exist "%CONDUCTOR_HOME%" mkdir "%CONDUCTOR_HOME%"
    curl -L -o "%JAR_PATH%" "%JAR_URL%"
    if errorlevel 1 (
        echo Failed to download Conductor Server JAR
        exit /b 1
    )
    echo Downloaded to %JAR_PATH%
)

REM Get server port from argument or prompt
if not "%~1"=="" (
    set SERVER_PORT=%~1
) else if not "%CONDUCTOR_PORT%"=="" (
    set SERVER_PORT=%CONDUCTOR_PORT%
) else (
    set SERVER_PORT=8080
    set /p SERVER_PORT="Enter the port for Server [8080]: "
    if "%SERVER_PORT%"=="" set SERVER_PORT=8080
)

echo Starting Conductor Server on port %SERVER_PORT%...
java -jar "%JAR_PATH%" --server.port=%SERVER_PORT%
