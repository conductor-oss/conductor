## Conductor: gRPC Go client generation
At the moment, the generation of the go client is manual.
In order to generate the Go gRPC client, run: 
```
make generateClient
```
This should create a folder under `<PROJECT_ROOT>/client/gogrpc/generated`. If there are changes on your proto files,
you will probably need to review these changes under `generated` and commit them.