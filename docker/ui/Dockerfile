#
# conductor:ui - Netflix conductor UI
#
FROM node

MAINTAINER Netflix OSS <conductor@netflix.com>

#ENV WF_SERVER "conductor-server:8080/api"
#ENV WEBSITE_HOSTNAME "conductor-server:8080"

COPY ./bin /

# Get all the dependencies
RUN apt-get update -y \
  && apt-get -y install git \

  # Chmod scripts
  && chmod +x startup.sh

# Get and install conductor UI
RUN git clone https://github.com/netflix/conductor.git \

  # Get UI project
  && mv /conductor/ui / \

  # Remove the conductor project
  && rm -rf conductor \

  # Install UI packages
  && cd /ui \
  && npm install

EXPOSE 3000

CMD ["/startup.sh"]
ENTRYPOINT ["/bin/bash"]
