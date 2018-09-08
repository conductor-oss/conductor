FROM golang:1.11-stretch

RUN apt-get update && apt-get install unzip

COPY . /conductor
WORKDIR /conductor

RUN cd /conductor/client/gogrpc && make bootstrap && make proto && make build && make test
