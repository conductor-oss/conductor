## Conductor: gRPC Go client generation
At the moment, the generation of the go client is manual.
You can generate the Go gRPC client through docker doing the following:
- On the project directory, create/update the latest proto files 
```
./gradlew clean build
```
This should update the `grpc` folder with the latest proto definitions.
- Build the docker image in charge of generating, building and testing the client.
```
docker build -f client/gogrpc/Dockerfile . -t protocontainer
```
- Once the build passes, we can generate the client on demand doing the following:
```
docker run --rm -it -v $PWD/client/gogrpc/generated:'/conductor/client/gogrpc/generated' protocontainer:latest
```
This should create a folder under `<PROJECT_ROOT>/client/gogrpc/generated`.