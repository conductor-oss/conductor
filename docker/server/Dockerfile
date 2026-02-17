# ===========================================================================================================
# Backend Builder stage
# ===========================================================================================================
FROM azul/zulu-openjdk-debian:21 AS builder

ARG INDEXING_BACKEND=elasticsearch

LABEL maintainer="Orkes OSS <oss@orkes.io>"

# Copy the project directly onto the image
COPY . /conductor
WORKDIR /conductor

# Build the server on run
RUN ./gradlew build -x test -x spotlessCheck -x shadowJar -x :conductor-os-persistence-v3:build -PindexingBackend=${INDEXING_BACKEND}
WORKDIR /server/build/libs


# ===========================================================================================================
# UI Builder stage
# ===========================================================================================================
FROM node:lts AS ui-builder
ARG YARN_OPTS

LABEL maintainer="Orkes OSS <oss@orkes.io>"

# Install dependencies
RUN corepack enable && corepack prepare yarn@stable --activate

COPY ui /conductor/ui
WORKDIR /conductor/ui

# Include monaco sources into bundle (instead of using CDN)
ENV REACT_APP_MONACO_EDITOR_USING_CDN=false
ENV REACT_APP_ENABLE_ERRORS_INSPECTOR=true
RUN yarn config set network-timeout 600000 -g
RUN yarn ${YARN_OPTS} install && cp -r node_modules/monaco-editor public/ && yarn ${YARN_OPTS} build


# ===========================================================================================================
# Bin builder stage
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
COPY docker/server/nginx/nginx.conf  /etc/nginx/conf.d/default.conf

# Copy the files for the server into the app folders
RUN chmod +x /app/startup.sh

HEALTHCHECK --interval=60s --timeout=30s --retries=10 CMD curl -I -XGET http://localhost:8080/health || exit 1

CMD [ "/app/startup.sh" ]
ENTRYPOINT [ "/bin/sh"]
