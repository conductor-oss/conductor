FROM golang:1.11-stretch

RUN apt-get update && apt-get install unzip

COPY . /conductor
WORKDIR /conductor/client/gogrpc

#Installs all dependencies, creates a build of the grpc client and tests it
RUN make bootstrap && make proto && make build && make test

#Entrypoint for generation of grpc client on demand
RUN chmod +x entrypoint.sh
ENTRYPOINT [ "./entrypoint.sh" ]
