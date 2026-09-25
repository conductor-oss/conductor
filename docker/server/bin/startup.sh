#!/bin/sh
#
#  Copyright 2023 Conductor authors
#  <p>
#  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
#  the License. You may obtain a copy of the License at
#  <p>
#  http://www.apache.org/licenses/LICENSE-2.0
#  <p>
#  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
#  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
#  specific language governing permissions and limitations under the License.
#

# startup.sh - startup script for the server docker image

echo "Starting Conductor server"

echo "Running Nginx in background"
# Start nginx as daemon
nginx

# Start the server
cd /app/libs
echo "Using java options config: $JAVA_OPTS"

# Alpine/musl: preload the glibc-compat shims (gcompat + libunwind, installed in the Dockerfile)
# so grpc-java's native netty bits don't segfault the JVM at load time. Scoped to the java process
# here — set after nginx has already started so nginx/busybox stay on native musl.
# DO NOT REMOVE WHILE WE ARE RUNNING ALPINE! (see Dockerfile for context)
export LD_PRELOAD=/lib/libgcompat.so.0:/usr/lib/libunwind.so.8

if [ -z "$CONFIG_PROP" ];
  then
    echo "No CONFIG_PROP set — using built-in defaults (SQLite, no external dependencies required)";
    java ${JAVA_OPTS} -jar conductor-server.jar 2>&1 | tee -a /app/logs/server.log
  else
    echo "Using config: $CONFIG_PROP";
    java ${JAVA_OPTS} -DCONDUCTOR_CONFIG_FILE=/app/config/$CONFIG_PROP -jar conductor-server.jar 2>&1 | tee -a /app/logs/server.log
fi
