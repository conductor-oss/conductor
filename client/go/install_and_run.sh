#!/bin/bash

CURR_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
GO_CONDUCTOR_DIR=$GOPATH/src/conductor

$CURR_DIR/install.sh

go run $GO_CONDUCTOR_DIR/startclient/startclient.go
