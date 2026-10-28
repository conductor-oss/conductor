#
# conductor:server - Conductor Server + UI
#
# Build args:
#   PREBUILT=true:  Skips both JAR and UI builds, uses pre-built artifacts (CI multi-arch)
#   SKIP_UI=true:   Skips only the UI build (E2E CI — tests serve UI from the host)
#   Default:        Builds JAR and UI from source (docker-compose, local dev)
#

# Both Java stages use the same patched JVM release and OS-managed trust bundle.
FROM debian:trixie-slim AS java-base

ARG TEMURIN_VERSION=25.0.5.0.0+8-0
ARG CA_CERTIFICATES_MIN_VERSION=20250419+deb13u1

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && dpkg --compare-versions "$(dpkg-query -W -f='${Version}' ca-certificates)" ge "$CA_CERTIFICATES_MIN_VERSION" \
    && install -d -m 0755 /etc/apt/keyrings \
    && curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public -o /etc/apt/keyrings/adoptium.asc \
    && echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb trixie main" > /etc/apt/sources.list.d/adoptium.list \
    && rm -rf /var/lib/apt/lists/*

FROM java-base AS jdk

RUN apt-get update \
    && apt-get install -y --no-install-recommends "temurin-25-jdk=${TEMURIN_VERSION}" \
    && rm -rf /var/lib/apt/lists/*

# ===========================================================================================================
# 0. Builder stage
# ===========================================================================================================
FROM jdk AS builder

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
FROM java-base AS runtime

LABEL maintainer="Orkes OSS <oss@orkes.io>"

RUN apt-get update \
    && apt-get install -y --no-install-recommends "temurin-25-jre=${TEMURIN_VERSION}" nginx \
    && rm -f /etc/nginx/sites-enabled/default \
    && rm -rf /var/lib/apt/lists/*

FROM runtime AS final

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
COPY docker/server/nginx/nginx.conf  /etc/nginx/conf.d/default.conf

# Copy the files for the server into the app folders
RUN chmod +x /app/startup.sh

HEALTHCHECK --interval=60s --timeout=30s --retries=10 CMD curl -I -XGET http://localhost:8080/health || exit 1

CMD [ "/app/startup.sh" ]
ENTRYPOINT [ "/bin/sh"]
