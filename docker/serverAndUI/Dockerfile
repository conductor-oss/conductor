#
# conductor:serverAndUI - Combined Netflix conductor server & UI 
#
# ===========================================================================================================
# 0. Builder stage
# ===========================================================================================================
FROM openjdk:11-jdk AS builder
LABEL maintainer="Netflix OSS <conductor@netflix.com>"

# Install Node
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN curl -sL https://deb.nodesource.com/setup_14.x | bash - \
  && curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add - \
  && echo "deb https://dl.yarnpkg.com/debian/ stable main" | tee /etc/apt/sources.list.d/yarn.list \
  && apt-get update -qq \
  && apt-get install -qq --no-install-recommends \
    build-essential \
    nodejs \
    yarn \
  && apt-get upgrade -qq \
  && rm -rf /var/lib/apt/lists/*

# Copy the project onto the builder image
COPY . /conductor

# Build the server
WORKDIR /conductor
RUN ./gradlew build -x test

# Build the client
WORKDIR /conductor/ui
RUN yarn install && yarn build

# ===========================================================================================================
# 1. Bin stage
# ===========================================================================================================

FROM nginx:alpine
RUN apk add openjdk11-jre

LABEL maintainer="Netflix OSS <conductor@netflix.com>"

# Make app folders
RUN mkdir -p /app/config /app/logs /app/libs

# Copy the compiled output to new image
COPY --from=builder /conductor/docker/serverAndUI/bin /app
COPY --from=builder /conductor/docker/serverAndUI/config /app/config
COPY --from=builder /conductor/server/build/libs/conductor-server-*-boot.jar /app/libs

# Copy compiled UI assets to nginx www directory
WORKDIR /usr/share/nginx/html
RUN rm -rf ./*
COPY --from=builder /conductor/ui/build .
COPY --from=builder /conductor/docker/serverAndUI/nginx/nginx.conf /etc/nginx/conf.d/default.conf

# Copy the files for the server into the app folders
RUN chmod +x /app/startup.sh

HEALTHCHECK --interval=60s --timeout=30s --retries=10 CMD curl -I -XGET http://localhost:8080/health || exit 1

CMD [ "/app/startup.sh" ]
ENTRYPOINT [ "/bin/sh"]
