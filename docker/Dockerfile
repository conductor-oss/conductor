#
# conductor:server - Netflix conductor Server
#
FROM java:8-jdk

MAINTAINER Netflix OSS <conductor@netflix.com>

COPY ./tmp /

# Get all the dependencies
RUN add-apt-repository -y ppa:cwchien/gradle \
  && apt-get update -y \
  && apt-get -y install curl netcat iputils-ping gradle \

  # Chmod scripts
  && chmod +x startup.sh \

  # Get node
  && curl -sL https://deb.nodesource.com/setup_6.x |  bash - \
  && apt-get install -y git nodejs build-essential \

# Get and install conductor
RUN git clone https://github.com/Netflix/conductor.git \
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
  && rm -rf conductor


EXPOSE 3000 8080

CMD ["/startup.sh"]
ENTRYPOINT ["/bin/bash"]
