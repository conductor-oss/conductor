#
# conductor:server - Combined Netflix conductor server & UI
#
# ===========================================================================================================
# 0. Builder stage
# ===========================================================================================================
FROM alpine:3.18 AS builder

LABEL maintainer="Netflix OSS <conductor@netflix.com>"

# ===========================================================================================================
# 0. Build Conductor Server
# ===========================================================================================================


# Install dependencies
RUN apk add openjdk17
RUN apk add git
RUN apk add --update nodejs npm yarn

COPY . /conductor
WORKDIR /conductor/ui
RUN yarn install && yarn build
RUN ls -ltr
RUN echo "Done building UI"

# Checkout the community project
WORKDIR /
RUN mkdir server-build
WORKDIR  server-build
RUN ls -ltr

RUN git clone https://github.com/Netflix/conductor-community.git

# Copy the project directly onto the image
WORKDIR conductor-community
RUN ls -ltr

# Build the server on run
RUN ./gradlew build -x test --stacktrace
WORKDIR /server-build
RUN ls -ltr
RUN pwd


# ===========================================================================================================
# 1. Bin stage
# ===========================================================================================================
FROM alpine:3.18

LABEL maintainer="Netflix OSS <conductor@netflix.com>"

RUN apk add openjdk17
RUN apk add nginx

# Make app folders
RUN mkdir -p /app/config /app/logs /app/libs

# Copy the compiled output to new image
COPY docker/server/bin /app
COPY docker/server/config /app/config
COPY --from=builder /server-build/conductor-community/community-server/build/libs/*boot*.jar /app/libs/conductor-server.jar

# Copy compiled UI assets to nginx www directory
WORKDIR /usr/share/nginx/html
RUN rm -rf ./*
COPY --from=builder /conductor/ui/build .
COPY --from=builder /conductor/docker/server/nginx/nginx.conf /etc/nginx/http.d/default.conf

# Copy the files for the server into the app folders
RUN chmod +x /app/startup.sh

HEALTHCHECK --interval=60s --timeout=30s --retries=10 CMD curl -I -XGET http://localhost:8080/health || exit 1

CMD [ "/app/startup.sh" ]
ENTRYPOINT [ "/bin/sh"]
