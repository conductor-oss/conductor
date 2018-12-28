#
# conductor:server - Netflix conductor server
#

# 0. Builder stage
FROM openjdk:8-jdk AS builder

MAINTAINER Netflix OSS <conductor@netflix.com>

# Copy the project directly onto the image
COPY . /conductor
WORKDIR /conductor

# Build the server on run
RUN ./gradlew build -x test

# 1. Bin stage
FROM openjdk:8-jre-alpine

MAINTAINER Netflix OSS <conductor@netflix.com>

# Make app folders
RUN mkdir -p /app/config /app/logs /app/libs

# Copy the project directly onto the image
COPY --from=builder /conductor/docker/server/bin /app
COPY --from=builder /conductor/docker/server/config /app/config
COPY --from=builder /conductor/server/build/libs/conductor-server-*-all.jar /app/libs

# Copy the files for the server into the app folders
RUN chmod +x /app/startup.sh

EXPOSE 8080
EXPOSE 8090

CMD [ "/app/startup.sh" ]
ENTRYPOINT [ "/bin/sh"]
