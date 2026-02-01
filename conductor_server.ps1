# Conductor Server Startup Script for Windows PowerShell
# Downloads the server JAR if missing and starts it with java
#
# Usage:
#   Interactive:  .\conductor_server.ps1
#   With port:    .\conductor_server.ps1 9090
#   One-liner:    irm https://raw.githubusercontent.com/conductor-oss/conductor/main/conductor_server.ps1 | iex

param(
    [Parameter(Position=0)]
    [string]$Port
)

$JAR_URL = "https://oss-releases.s3.us-east-1.amazonaws.com/conductor-server-latest.jar"
$JAR_NAME = "conductor-server-latest.jar"

# Use CONDUCTOR_HOME if set, otherwise use current directory
if ([string]::IsNullOrEmpty($env:CONDUCTOR_HOME)) {
    $CONDUCTOR_HOME = "."
} else {
    $CONDUCTOR_HOME = $env:CONDUCTOR_HOME
}

$JAR_PATH = Join-Path $CONDUCTOR_HOME $JAR_NAME

# Download JAR if not present
if (-not (Test-Path $JAR_PATH)) {
    Write-Host "Downloading Conductor Server..."
    if (-not (Test-Path $CONDUCTOR_HOME)) {
        New-Item -ItemType Directory -Path $CONDUCTOR_HOME -Force | Out-Null
    }
    try {
        Invoke-WebRequest -Uri $JAR_URL -OutFile $JAR_PATH
        Write-Host "Downloaded to $JAR_PATH"
    } catch {
        Write-Host "Failed to download Conductor Server JAR: $_"
        exit 1
    }
}

# Get server port from argument, environment, or prompt
if (-not [string]::IsNullOrEmpty($Port)) {
    $SERVER_PORT = $Port
} elseif (-not [string]::IsNullOrEmpty($env:CONDUCTOR_PORT)) {
    $SERVER_PORT = $env:CONDUCTOR_PORT
} elseif ([Environment]::UserInteractive) {
    $SERVER_PORT = Read-Host "Enter the port for Server [8080]"
    if ([string]::IsNullOrEmpty($SERVER_PORT)) { $SERVER_PORT = "8080" }
} else {
    $SERVER_PORT = "8080"
}

Write-Host "Starting Conductor Server on port $SERVER_PORT..."
java -jar $JAR_PATH --server.port=$SERVER_PORT
