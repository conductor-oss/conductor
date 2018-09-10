#!/bin/bash

set -e

printUsage() {
    echo "gen-proto generates grpc and protobuf"
    echo " "
    echo "Usage: gen-proto --protoDir myProtoDir --output ."
    echo " "
    echo "options:"
    echo " -h, --help           Show help"
    echo " --protoDir DIR       Scans the given directory for all proto files"
    echo " --output DIR         The output directory for generated files. Will be automatically created."
}

OUTPUT_DIR=""

while test $# -gt 0; do
    case "$1" in
        -h|--help)
            printUsage
            exit 0
            ;;
        --protoDir)
            shift
            if test $# -gt 0; then
                PROTO_DIR=$1
            else
                echo "Directory not specified for consuming proto files"
                exit 1
            fi
            shift
            ;;
        --output)
            shift
            OUTPUT_DIR=$1
            shift
            ;;
    esac
done

if [[ -z $PROTO_DIR ]]; then
    echo "Error: You must specify a proto directory"
    printUsage
    exit 1
fi

if [[ $OUTPUT_DIR == '' ]]; then
    OUTPUT_DIR="."
fi

OUTPUT_DIR="${OUTPUT_DIR}/generated"

echo "Generating Go files for ${PROTO_DIR} in $OUTPUT_DIR"

## TODO
## Think about make and where the go_out should live

if [[ ! -d $OUTPUT_DIR ]]; then
    mkdir -p $OUTPUT_DIR
fi


GEN_STRING="--go_out=${GO_SOURCE_RELATIVE}plugins=grpc:$OUT_DIR"


