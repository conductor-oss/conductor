#!/bin/bash

set -e

printUsage() {
    echo "./entrypoint.sh generates grpc client"
    echo " "
    echo "Usage: ./entrypoint.sh --dir myProtoDir --output ."
    echo " "
    echo "options:"
    echo " -h, --help          Show help"
    echo " -d, --dir DIR       Scans the given directory for all proto files"
    echo " -o, --output DIR    The output directory for generated files. Will be automatically created."
}

PROTO_DIR="/conductor/grpc/src/main/proto"
OUTPUT_DIR=""

while test $# -gt 0; do
    case "$1" in
        -h|--help)
            printUsage
            exit 0
            ;;
        -d|--dir)
            shift
            if test $# -gt 0; then
                PROTO_DIR=$1
            else
                echo "Directory not specified for consuming proto files"
                exit 1
            fi
            shift
            ;;
        -o|--output)
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

if [[ ! -d $OUTPUT_DIR ]]; then
    mkdir -p $OUTPUT_DIR
fi


make PROTO_SRC=$PROTO_DIR GO_OUTPUT=$OUTPUT_DIR proto


