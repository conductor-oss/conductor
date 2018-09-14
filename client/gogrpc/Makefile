PROTO_SRC = ../../grpc/src/main/proto
PROTO_VERSION = 3.5.1

BUILD_DIR = build
BIN_DIR = $(BUILD_DIR)/bin
SERVICES = \
    $(PROTO_SRC)/grpc/event_service.pb.go \
    $(PROTO_SRC)/grpc/metadata_service.pb.go \
    $(PROTO_SRC)/grpc/search.pb.go \
    $(PROTO_SRC)/grpc/task_service.pb.go \
    $(PROTO_SRC)/grpc/workflow_service.pb.go

USER_ID := $(shell id -u)
GROUP_ID := $(shell id -g)
CONDUCTOR_ROOT = /go/src/github.com/netflix/conductor
PROTOC = docker run --rm -it \
	--user $(USER_ID):$(GROUP_ID) \
	-v '$(PWD)/../..':'$(CONDUCTOR_ROOT)' \
	-w $(CONDUCTOR_ROOT)/client/gogrpc \
	znly/protoc:0.3.0 

proto: models $(SERVICES)

build:
	go fmt ./...
	go build ./...

test:
	go test ./...

# Helpers
$(SERVICES): %.pb.go: %.proto
	$(PROTOC) -I $(PROTO_SRC) $< --go_out=plugins=grpc:/go/src

models:
	$(PROTOC) -I $(PROTO_SRC) $(PROTO_SRC)/model/*.proto --go_out=/go/src