FROM golang:1.11-stretch as builder

RUN apt-get update && apt-get install unzip

WORKDIR /go/src/github.com/netflix/conductor

ADD ./client ./client
ADD ./grpc ./grpc

ENV GO111MODULE on
RUN cd client/gogrpc && make bootstrap proto test

ENTRYPOINT ["cp", "-r", "client/gogrpc/conductor/grpc", "client/gogrpc/conductor/model", "/generated"]
