PROTO_SRC = ../../grpc/src/main/proto
PROTO_VERSION = 3.5.1
GO_OUTPUT=$(GOPATH)/src

UNAME_S := $(shell uname -s)
OSFLAG:=
ifeq ($(UNAME_S),Linux)
	OS = linux
endif
ifeq ($(UNAME_S),Darwin)
	OS = osx
endif

BUILD_DIR = build
BIN_DIR = $(BUILD_DIR)/bin
SERVICES = \
    $(PROTO_SRC)/grpc/event_service.pb.go \
    $(PROTO_SRC)/grpc/metadata_service.pb.go \
    $(PROTO_SRC)/grpc/search.pb.go \
    $(PROTO_SRC)/grpc/task_service.pb.go \
    $(PROTO_SRC)/grpc/workflow_service.pb.go

bootstrap: install-protoc
	go install github.com/golang/protobuf/protoc-gen-go
	go install github.com/square/goprotowrap/cmd/protowrap

proto: models $(SERVICES)

build:
	go fmt ./...
	go build ./...

test:
	go test ./...

# Helpers
$(SERVICES): %.pb.go: %.proto
	$(BIN_DIR)/protoc -I $(PROTO_SRC) $< --go_out=plugins=grpc:$(GO_OUTPUT)

models:
	$(BIN_DIR)/protoc -I $(PROTO_SRC) $(PROTO_SRC)/model/*.proto --go_out=$(GO_OUTPUT)

install-protoc:
	mkdir -p $(BIN_DIR)
	curl -Lso $(BUILD_DIR)/protoc.zip https://github.com/protocolbuffers/protobuf/releases/download/v$(PROTO_VERSION)/protoc-$(PROTO_VERSION)-$(OS)-x86_64.zip
	cd $(BUILD_DIR); unzip protoc.zip
