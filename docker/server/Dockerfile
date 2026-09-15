#
# conductor:server - Conductor Server + UI
#
# Build args:
#   PREBUILT=true:  Skips both JAR and UI builds, uses pre-built artifacts (CI multi-arch)
#   SKIP_UI=true:   Skips only the UI build (E2E CI — tests serve UI from the host)
#   Default:        Builds JAR and UI from source (docker-compose, local dev)
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
      ./gradlew build -x test -x spotlessCheck -x shadowJar \
        -PindexingBackend=${INDEXING_BACKEND} \
        -Dorg.gradle.jvmargs=-Xmx2g \
        --no-daemon --no-parallel; \
    fi


# ===========================================================================================================
# 1. UI builder stage
# ===========================================================================================================
FROM node:lts AS ui-builder

ARG PREBUILT=false
ARG SKIP_UI=false

LABEL maintainer="Orkes OSS <oss@orkes.io>"

COPY ui-next /conductor/ui-next
WORKDIR /conductor/ui-next

RUN if [ "$SKIP_UI" = "true" ]; then \
      mkdir -p dist && echo "UI build skipped (SKIP_UI=true)"; \
    elif [ "$PREBUILT" = "false" ]; then \
      corepack enable && \
      pnpm install && \
      NODE_OPTIONS=--max-old-space-size=4096 pnpm build; \
    else \
      echo "Using pre-built UI assets"; \
    fi

# ===========================================================================================================
# 2. Final stage
# ===========================================================================================================
# Alpine base to shed the OS-level CVEs that debian:stable-slim carried: perl
# (CVE-2026-13221 / CVE-2026-57432), libssh2 (CVE-2026-58050), and an nginx that debian-slim
# could not patch past 1.26.3 (CVE-2026-60005). The temurin JRE ships in the base image, so the
# only package we add is nginx. The healthcheck uses busybox wget (built in) instead of curl,
# which also removes curl's libcurl/libssh2 chain from the image entirely.
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Orkes OSS <oss@orkes.io>"

RUN apk add --no-cache nginx ca-certificates \
    && rm -f /etc/nginx/http.d/default.conf \
    && mkdir -p /run/nginx

# Make app folders
RUN mkdir -p /app/config /app/logs /app/libs

# Copy the compiled output to new image
COPY docker/server/bin /app
COPY docker/server/config /app/config
COPY --from=builder /conductor/server/build/libs/*boot*.jar /app/libs/conductor-server.jar

# Copy compiled UI assets to nginx www directory
WORKDIR /usr/share/nginx/html
RUN rm -rf ./*
COPY --from=ui-builder /conductor/ui-next/dist .
COPY docker/server/nginx/nginx.conf  /etc/nginx/http.d/default.conf

# Copy the files for the server into the app folders
RUN chmod +x /app/startup.sh

# Uses 127.0.0.1 (not localhost): busybox wget resolves localhost to ::1 first and does not fall
# back to IPv4 the way curl did, so localhost would fail against the IPv4-bound server.
HEALTHCHECK --interval=60s --timeout=30s --retries=10 CMD wget -q -O /dev/null http://127.0.0.1:8080/health || exit 1

CMD [ "/app/startup.sh" ]
ENTRYPOINT [ "/bin/sh"]
