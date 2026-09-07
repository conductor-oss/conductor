#!/bin/sh
# Build ui-next and copy assets into the server resource directories.
# This is the ui-next equivalent of build_ui.sh — the original build_ui.sh
# (which builds ui/ with yarn) is left unchanged.
set -e

cd ui-next
pwd
pnpm install
pnpm build
echo "Done building ui-next, copying dist to server"
cd ..
pwd
mkdir -p server/src/main/resources/static
rm -rf server/src/main/resources/static/*
cp -r ui-next/dist/. server/src/main/resources/static/
