PROTO_SRC = ../../grpc/src/main/proto

SERVICES = \
    $(PROTO_SRC)/grpc/event_service.pb.go \
    $(PROTO_SRC)/grpc/metadata_service.pb.go \
    $(PROTO_SRC)/grpc/search.pb.go \
    $(PROTO_SRC)/grpc/task_service.pb.go \
    $(PROTO_SRC)/grpc/workflow_service.pb.go

$(SERVICES): %.pb.go: %.proto
	protoc -I $(PROTO_SRC) $< --go_out=plugins=grpc:$(GOPATH)/src

models:
	protoc -I $(PROTO_SRC) $(PROTO_SRC)/model/*.proto --go_out=$(GOPATH)/src

proto: models $(SERVICES)