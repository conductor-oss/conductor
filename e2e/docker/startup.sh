#!/bin/sh
#
# startup.sh - startup script for the e2e test server docker image (no UI)
#

echo "Starting Conductor server"

cd /app/libs
echo "Property file: $CONFIG_PROP"

if [ -z "$CONFIG_PROP" ]; then
    echo "Using default configuration file"
    export config_file=/app/config/config-redis.properties
else
    echo "Using '$CONFIG_PROP'"
    export config_file=/app/config/$CONFIG_PROP
fi

echo "Using java options config: $JAVA_OPTS"

java ${JAVA_OPTS} -jar -DCONDUCTOR_CONFIG_FILE=$config_file conductor-server.jar 2>&1
