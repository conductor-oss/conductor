#
# conductor:ui - Conductor UI
#
# ===========================================================================================================
# 1. Build UI
# ===========================================================================================================
FROM node:20-alpine AS build
LABEL maintainer="Orkes OSS <oss@orkes.io>"

# Install the required packages for the node build
# to run on alpine
RUN apk update && apk add --no-cache python3 py3-pip make g++

# A directory within the virtualized Docker environment
# Becomes more relevant when using Docker Compose later
WORKDIR /usr/src/app

# Copies everything else over to Docker environment
# node_modules excluded in .dockerignore.
COPY ./ui .

# Installs all node packages. Cached unless package.json changes
RUN yarn install && mkdir -p public && cp -r node_modules/monaco-editor public/

# Include monaco sources into bundle (instead of using CDN)
ENV REACT_APP_MONACO_EDITOR_USING_CDN=false
ENV REACT_APP_ENABLE_ERRORS_INSPECTOR=true

# build project to host on a production web server
RUN yarn build

# ===========================================================================================================
# 2. Use Nginx as the production server
# ===========================================================================================================

FROM nginx:alpine

WORKDIR /usr/share/nginx/html

# copy built project
COPY --from=build /usr/src/app/build .

# copy default nginx config
COPY ./docker/server/nginx/nginx.conf /etc/nginx/conf.d/nginx.conf.template 
COPY ./docker/ui/bin/startup-ui.sh .

EXPOSE 5000

CMD [ "/usr/share/nginx/html/startup-ui.sh" ]
ENTRYPOINT [ "/bin/sh"]
