#
# conductor:server - Conductor Server + UI
#
# This Dockerfile expects pre-built artifacts in the build context:
#   - docker/server/libs/conductor-server.jar  (server JAR, staged by CI)
#   - ui/build/                                (UI static files, built on host)
#
# The JAR is platform-independent (Java bytecode), so there is no need to
# rebuild inside each architecture's container. Only the JRE layer is
# architecture-specific, which Docker multi-arch handles automatically.
#

FROM debian:stable-slim

LABEL maintainer="Orkes OSS <oss@orkes.io>"

RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-21-jre-headless nginx curl ca-certificates \
    && rm -f /etc/nginx/sites-enabled/default \
    && rm -rf /var/lib/apt/lists/*

# Make app folders
RUN mkdir -p /app/config /app/logs /app/libs

# Copy the pre-built server JAR
COPY docker/server/bin /app
COPY docker/server/config /app/config
COPY docker/server/libs/conductor-server.jar /app/libs/conductor-server.jar

# Copy pre-built UI assets to nginx www directory
WORKDIR /usr/share/nginx/html
RUN rm -rf ./*
COPY ui/build .
COPY docker/server/nginx/nginx.conf /etc/nginx/conf.d/default.conf

# Copy the files for the server into the app folders
RUN chmod +x /app/startup.sh

HEALTHCHECK --interval=60s --timeout=30s --retries=10 CMD curl -I -XGET http://localhost:8080/health || exit 1

CMD [ "/app/startup.sh" ]
ENTRYPOINT [ "/bin/sh"]
