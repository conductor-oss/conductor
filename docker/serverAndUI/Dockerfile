#
# conductor:serverAndUI - Netflix conductor server and UI
#
FROM java:8-jdk

MAINTAINER Netflix OSS <conductor@netflix.com>

COPY ./bin ./conf /

# Get all the dependencies
RUN apt-get update -y \
  && apt-get install -y software-properties-common python-software-properties \
  && add-apt-repository -y ppa:cwchien/gradle \
  && apt-get -y install git curl netcat iputils-ping gradle \

  # Chmod scripts
  && chmod +x startup.sh \

  # Get node
  && curl -sL https://deb.nodesource.com/setup_6.x |  bash - \
  && apt-get install -y nodejs build-essential

# Get and install conductor
RUN git clone https://github.com/Netflix/conductor.git \
  && cd conductor \
  && ./gradlew build -x test \

  # Get Server Jar
  && mv ./server/build/libs/conductor-server-*-all.jar / \

  # Get UI project
  && mv ./ui / \

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
