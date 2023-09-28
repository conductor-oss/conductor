#
# conductor:ui - Netflix Conductor UI
#
FROM node:20-alpine
LABEL maintainer="Netflix OSS <conductor@netflix.com>"

# Install the required packages for the node build
# to run on alpine
RUN apk update && apk add --no-cache python3 py3-pip make g++

# A directory within the virtualized Docker environment
# Becomes more relevant when using Docker Compose later
WORKDIR /usr/src/app

# Copies package.json to Docker environment in a separate layer as a performance optimization
COPY ./ui/package.json ./

# Installs all node packages. Cached unless package.json changes
RUN yarn install

# Copies everything else over to Docker environment
# node_modules excluded in .dockerignore.
COPY ./ui .

CMD [ "yarn", "start" ]
