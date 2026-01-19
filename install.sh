#!/bin/bash
set -e

# Configuration
CONDUCTOR_DIR="$HOME/.conductor"
JDK_DIR="$CONDUCTOR_DIR/jdk"
JAR_PATH="$CONDUCTOR_DIR/conductor-server-lite.jar"
REPO="conductor-oss/conductor"
MIN_JAVA_VERSION=21

# Create installation directory
mkdir -p "$CONDUCTOR_DIR"

log() {
    echo "[Conductor Installer] $1"
}

# Function to check Java version
check_java_version() {
    if command -v java >/dev/null 2>&1; then
        version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print $1}')
        if [ "$version" -ge "$MIN_JAVA_VERSION" ]; then
            return 0
        fi
    fi
    return 1
}

# 1. Setup Java
JAVA_CMD="java"
if check_java_version; then
    log "Found compatible Java version."
else
    log "Java $MIN_JAVA_VERSION+ not found. Downloading..."
    
    OS=$(uname -s | tr '[:upper:]' '[:lower:]')
    ARCH=$(uname -m)
    
    # Map Architecture
    if [ "$ARCH" = "x86_64" ]; then
        ARCH="x64"
    elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
        ARCH="aarch64"
    fi

    # Map OS
    if [ "$OS" = "darwin" ]; then
        OS="mac"
    fi

    # Download JDK (Eclipse Temurin)
    JDK_URL="https://api.adoptium.net/v3/binary/latest/${MIN_JAVA_VERSION}/ga/${OS}/${ARCH}/jdk/hotspot/normal/eclipse?project=jdk"
    
    mkdir -p "$JDK_DIR"
    curl -L "$JDK_URL" -o "$JDK_DIR/jdk.tar.gz"
    
    tar -xzf "$JDK_DIR/jdk.tar.gz" -C "$JDK_DIR" --strip-components=1
    rm "$JDK_DIR/jdk.tar.gz"
    
    JAVA_CMD="$JDK_DIR/bin/java"
    log "Java installed to $JDK_DIR"
fi

# 2. Download Conductor JAR
log "Downloading Conductor Server Lite..."
LATEST_RELEASE_URL="https://api.github.com/repos/$REPO/releases/latest"
DOWNLOAD_URL=$(curl -s "$LATEST_RELEASE_URL" | grep "browser_download_url.*standalone.jar" | cut -d '"' -f 4)

if [ -z "$DOWNLOAD_URL" ]; then
    log "Error: Could not find standalone JAR in latest release."
    exit 1
fi

curl -L "$DOWNLOAD_URL" -o "$JAR_PATH"
log "Downloaded to $JAR_PATH"

# 3. Create Launch Script
LAUNCHER="$CONDUCTOR_DIR/start-conductor.sh"
cat > "$LAUNCHER" <<EOF
#!/bin/bash
exec "$JAVA_CMD" -jar "$JAR_PATH" "\$@"
EOF
chmod +x "$LAUNCHER"

log "Installation complete!"
log "To start Conductor, run:"
log "  $LAUNCHER"

# 4. Optional: Run immediately
if [ "$1" == "--run" ]; then
    "$LAUNCHER"
fi
