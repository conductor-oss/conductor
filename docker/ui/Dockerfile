#
# conductor:ui - Netflix conductor UI
#
FROM node

MAINTAINER Netflix OSS <conductor@netflix.com>

# Make app folders
RUN mkdir -p /app/config /app/logs /app/libs

# Startup script(s)
COPY ./bin /app

# Get all the dependencies
RUN apt-get update -y \
  && apt-get -y install git \

  # Chmod scripts
  && chmod +x /app/startup.sh

# Get and install conductor UI
RUN git clone https://github.com/netflix/conductor.git \

  # Get UI project
  && mv /conductor/ui /app \

  # Remove the conductor project
  && rm -rf conductor \

  # Install UI packages
  && cd /app/ui \
  && npm install \
  && npm run build --server

EXPOSE 5000

CMD ["/app/startup.sh"]
ENTRYPOINT ["/bin/bash"]
