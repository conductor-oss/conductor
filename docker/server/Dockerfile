#
# conductor:server - Netflix conductor server
#

# ===========================================================================================================
# 0. Builder stage
# ===========================================================================================================
FROM eclipse-temurin:11-jdk-focal AS builder

LABEL maintainer="Netflix OSS <conductor@netflix.com>"

# Copy the project directly onto the image
COPY . /conductor
WORKDIR /conductor

# Build the server on run
RUN ./gradlew build -x test --stacktrace

# ===========================================================================================================
# 1. Bin stage
# ===========================================================================================================
FROM eclipse-temurin:11-jre-focal 

LABEL maintainer="Netflix OSS <conductor@netflix.com>"

# Make app folders
RUN mkdir -p /app/config /app/logs /app/libs

# Copy the compiled output to new image
COPY --from=builder /conductor/docker/server/bin /app
COPY --from=builder /conductor/docker/server/config /app/config
COPY --from=builder /conductor/server/build/libs/conductor-server-*-boot.jar /app/libs

# Copy the files for the server into the app folders
RUN chmod +x /app/startup.sh

HEALTHCHECK --interval=60s --timeout=30s --retries=10 CMD curl -I -XGET http://localhost:8080/health || exit 1

CMD [ "/app/startup.sh" ]
ENTRYPOINT [ "/bin/sh"]
