#
# conductor:server - Conductor Server for E2E Tests (no UI)
#
# ===========================================================================================================
# Runtime stage
# ===========================================================================================================
FROM azul/zulu-openjdk-debian:21-jre

LABEL maintainer="Conductor OSS <oss@conductor.io>"

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Make app folders
RUN mkdir -p /app/config /app/logs /app/libs

# Copy the pre-built jar (copied to e2e/docker/ before build to avoid .dockerignore)
COPY conductor-server.jar /app/libs/conductor-server.jar

# Copy config files
COPY config /app/config

# Copy startup script
COPY startup.sh /app/startup.sh
RUN chmod +x /app/startup.sh

WORKDIR /app

HEALTHCHECK --interval=10s --timeout=5s --retries=12 CMD curl -I -XGET http://localhost:8080/health || exit 1

ENTRYPOINT ["/bin/sh"]
CMD ["/app/startup.sh"]
