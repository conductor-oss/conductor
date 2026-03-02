#
# conductor:server - Conductor Server + UI
#
# Two modes:
#   Default:        Builds JAR and UI from source (docker-compose, local dev)
#   PREBUILT=true:  Skips builds, uses pre-built artifacts (CI multi-arch)
#

# ===========================================================================================================
# 0. Builder stage
# ===========================================================================================================
FROM azul/zulu-openjdk-debian:21 AS builder

ARG PREBUILT=false
ARG INDEXING_BACKEND=elasticsearch

LABEL maintainer="Orkes OSS <oss@orkes.io>"

COPY . /conductor
WORKDIR /conductor

RUN if [ "$PREBUILT" = "true" ]; then \
      rm -rf server/build/libs && mkdir -p server/build/libs && \
      cp docker/server/libs/conductor-server.jar server/build/libs/conductor-server-boot.jar && \
      echo "Using pre-built server JAR"; \
    else \
      ./gradlew build -x test -x spotlessCheck -x shadowJar -x :conductor-os-persistence-v3:build \
        -PindexingBackend=${INDEXING_BACKEND} \
        -Dorg.gradle.jvmargs=-Xmx2g \
        --no-daemon --no-parallel; \
    fi

# ===========================================================================================================
# 1. UI builder stage
# ===========================================================================================================
FROM node:lts AS ui-builder

ARG PREBUILT=false
ARG YARN_OPTS

LABEL maintainer="Orkes OSS <oss@orkes.io>"

COPY . /conductor
WORKDIR /conductor/ui

RUN if [ "$PREBUILT" = "false" ]; then \
      corepack enable && corepack prepare yarn@stable --activate && \
      export REACT_APP_MONACO_EDITOR_USING_CDN=false && \
      export REACT_APP_ENABLE_ERRORS_INSPECTOR=true && \
      yarn config set network-timeout 600000 -g && \
      yarn ${YARN_OPTS} install && cp -r node_modules/monaco-editor public/ && yarn ${YARN_OPTS} build; \
    else \
      echo "Using pre-built UI assets"; \
    fi

# ===========================================================================================================
# 2. Final stage
# ===========================================================================================================
FROM debian:stable-slim

LABEL maintainer="Orkes OSS <oss@orkes.io>"

RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-21-jre-headless nginx curl ca-certificates \
    && rm -f /etc/nginx/sites-enabled/default \
    && rm -rf /var/lib/apt/lists/*

# Make app folders
RUN mkdir -p /app/config /app/logs /app/libs

# Copy the compiled output to new image
COPY docker/server/bin /app
COPY docker/server/config /app/config
COPY --from=builder /conductor/server/build/libs/*boot*.jar /app/libs/conductor-server.jar

# Copy compiled UI assets to nginx www directory
WORKDIR /usr/share/nginx/html
RUN rm -rf ./*
COPY --from=ui-builder /conductor/ui/build .
COPY --from=ui-builder /conductor/docker/server/nginx/nginx.conf /etc/nginx/conf.d/default.conf

# Copy the files for the server into the app folders
RUN chmod +x /app/startup.sh

HEALTHCHECK --interval=60s --timeout=30s --retries=10 CMD curl -I -XGET http://localhost:8080/health || exit 1

CMD [ "/app/startup.sh" ]
ENTRYPOINT [ "/bin/sh"]
