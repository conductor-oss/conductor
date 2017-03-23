#!/bin/bash

CURR_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
GO_CONDUCTOR_DIR=$GOPATH/src/conductor

mkdir -p $GO_CONDUCTOR_DIR
cp -r $CURR_DIR/* $GO_CONDUCTOR_DIR

# Install dependencies
cd $GO_CONDUCTOR_DIR
go get
