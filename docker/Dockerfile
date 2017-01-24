#
# netflix-conductor: Netflix conductor Server
#
FROM java:8-jdk

MAINTAINER Netflix OSS <conductor@netflix.com>

RUN mkdir -p /app/config
RUN mkdir -p /app/logs
RUN mkdir -p /app/libs

# Startup script
COPY ./tmp/startup.sh /app/

# To be used with the docker-compose
COPY ./tmp/config.properties /app/config/

# In memory single node Conductor
COPY ./tmp/config-local.properties /app/config/

RUN apt-get update \
  && apt-get -y install curl netcat iputils-ping vim telnet \

  # Chmod scripts
  && chmod +x /app/startup.sh \

  # Get Node
  && curl -sL https://deb.nodesource.com/setup_6.x |  bash - \
  && apt-get install -y git nodejs build-essential \

  # Get Conductor
  && git clone https://github.com/Netflix/conductor.git \
  && cd conductor \
  && ./gradlew build -x test \

  # Copy Server Jar
  && mv ./server/build/libs/conductor-server-*-all.jar /app/libs/ \

  # Get UI project
  && mv ./ui /app \

  && pwd \
  && ls -ltr \

  # Install UI packages

  && cd /app/ui \
  && ls -ltr /app/ui/ \
  && npm install \
  && npm run build --server \

  # Go back to root
  && cd / \

  # Clean up
  && rm -rf conductor


EXPOSE 5000 8080

CMD ["/app/startup.sh"]
ENTRYPOINT ["/bin/bash"]
