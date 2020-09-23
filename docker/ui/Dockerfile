#
# conductor:ui - Netflix conductor UI
#
FROM node:9-alpine
MAINTAINER Netflix OSS <conductor@netflix.com>

# Install the required packages for the node build
# to run on alpine
RUN apk update && apk add \
  autoconf \
  automake \
  libtool \
  build-base \
  libstdc++ \
  gcc \
  abuild \
  binutils \
  nasm \
  libpng \
  libpng-dev \
  libjpeg-turbo \
  libjpeg-turbo-dev \
  python

# Make app folders
RUN mkdir -p /app/ui

# Copy the ui files onto the image
COPY ./docker/ui/bin /app
COPY ./ui /app/ui

# Copy the files for the server into the app folders
RUN chmod +x /app/startup.sh

# Get and install conductor UI
RUN cd /app/ui \
  && rm package-lock.json \
  && npm install \
  && npm run build --server

EXPOSE 5000

CMD [ "/app/startup.sh" ]
ENTRYPOINT ["/bin/sh"]
