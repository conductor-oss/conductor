PROTO_SRC = ../../grpc/src/main/proto

proto:
	protoc -I $(PROTO_SRC) $(PROTO_SRC)/model/*.proto --go_out=$(GOPATH)/src
	protoc -I $(PROTO_SRC) $(PROTO_SRC)/grpc/*.proto --go_out=plugins=grpc:$(GOPATH)/src