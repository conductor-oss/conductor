#
# conductor:serverAndUI - Netflix conductor server and UI
#
FROM openjdk:8-jdk

MAINTAINER Netflix OSS <conductor@netflix.com>

# Make app folders
RUN mkdir -p /app/config /app/logs /app/libs

# Startup script(s)
COPY ./bin /app

# Configs
COPY ./config /app/config

# Get all the dependencies
RUN apt-get update -y \
  && apt-get -y install git curl \

  # Chmod scripts
  && chmod +x /app/startup.sh \

  # Get node
  && curl -sL https://deb.nodesource.com/setup_10.x |  bash - \
  && apt-get install -y nodejs build-essential

# Get and install conductor
RUN git clone https://github.com/Netflix/conductor.git \
  && cd conductor \
  && ./gradlew build -x test \

  # Get Server Jar
  && mv ./server/build/libs/conductor-server-*-all.jar /app/libs/ \

  # Get UI project
  && mv ./ui /app \

  # Install UI packages
  && cd /app/ui \
  && npm install \
  && npm run build --server \

  # Go back to root
  && cd / \

  # Clean up
  && rm -rf conductor


EXPOSE 5000 8080

CMD ["/app/startup.sh"]
ENTRYPOINT ["/bin/bash"]
