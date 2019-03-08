// +build tools

package tools

import (
	_ "github.com/golang/protobuf/protoc-gen-go"
	_ "github.com/square/goprotowrap/cmd/protowrap"
	_ "github.com/kazegusuri/grpcurl"
)
