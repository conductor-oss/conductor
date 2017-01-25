#
# conductor:server - Netflix conductor server
#
FROM java:8-jdk

MAINTAINER Netflix OSS <conductor@netflix.com>

# Make app folders
RUN mkdir -p /app/config /app/logs /app/libs

# Startup script(s)
COPY ./bin /app

# Configs
COPY ./config /app/config

# Get all the dependencies
RUN apt-get update -y \
  && apt-get -y install git \

  # Chmod scripts
  && chmod +x /app/startup.sh

# Get and install conductor
RUN git clone https://github.com/Netflix/conductor.git \
  && cd conductor \
  && ./gradlew build -x test \

  # Get Server Jar
  && mv ./server/build/libs/conductor-server-*-all.jar /app/libs/ \

  # Go back to root
  && cd / \

  # Clean up
  && rm -rf conductor


EXPOSE 8080

CMD ["/app/startup.sh"]
ENTRYPOINT ["/bin/bash"]