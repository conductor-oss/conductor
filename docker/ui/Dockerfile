#
# conductor:ui - Conductor UI (ui-next)
#
# Standalone UI image: builds ui-next and serves the static bundle with nginx.
# API requests are proxied to the conductor server; point the container at
# your server with:
#
#   docker run -e WF_SERVER=http://my-conductor:8080 -p 5000:5000 conductor:ui
#
# Build from the repo root:
#
#   docker build -t conductor:ui -f docker/ui/Dockerfile .
#
# Note: the conductor:server image (docker/server/Dockerfile) already bundles
# this same UI — this image is only for running the UI separately.
#

# ===========================================================================================================
# 0. Build stage
# ===========================================================================================================
FROM node:lts AS builder
LABEL maintainer="Orkes OSS <oss@orkes.io>"

COPY ui-next /conductor/ui-next
WORKDIR /conductor/ui-next

RUN corepack enable && \
    pnpm install --frozen-lockfile && \
    NODE_OPTIONS=--max-old-space-size=4096 pnpm build

# ===========================================================================================================
# 1. Serve stage
# ===========================================================================================================
FROM nginx:stable-alpine
LABEL maintainer="Orkes OSS <oss@orkes.io>"

# Upstream conductor server for /api and /actuator; override at runtime.
ENV WF_SERVER=http://conductor-server:8080

COPY --from=builder /conductor/ui-next/dist /usr/share/nginx/html
# The official nginx image envsubst's templates into /etc/nginx/conf.d at start.
COPY docker/ui/nginx.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 5000
