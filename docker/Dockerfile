#
# netflix-conductor: Netflix conductor Server
#
FROM java:8-jdk

MAINTAINER Netflix OSS <conductor@netflix.com>

COPY ./tmp /

RUN apt-get update \
  && apt-get -y install curl netcat iputils-ping vim telnet \

  # Chmod scripts
  && chmod +x startup.sh \

  # Get Node
  && curl -sL https://deb.nodesource.com/setup_6.x |  bash - \
  && apt-get install -y git nodejs build-essential \

  # Get Conductor
  && git clone https://github.com/Netflix/conductor.git \
  && cd conductor \
  && ./gradlew build -x test \

  # Get Server Jar
  && mv ./server/build/libs/conductor-server-*-all.jar ../ \

  # Get UI project
  && mv ./ui ../ \

  # Install UI packages
  && cd /ui \
  && npm install \

  # Go back to root
  && cd / \

  # Clean up
  && rm -rf conductor \
  && apt-get remove -y curl


EXPOSE 3000 8080

CMD ["/startup.sh"]
ENTRYPOINT ["/bin/bash"]
