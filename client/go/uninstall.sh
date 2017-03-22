#!/bin/bash

CURR_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
GO_CONDUCTOR_DIR=$GOPATH/src/conductor

rm -rf $GO_CONDUCTOR_DIR
