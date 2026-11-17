#!/bin/bash
# Runs TLC on one model: ./run.sh MC_Compose
J=${JAVA:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java}
T=${TLA2TOOLS:?set TLA2TOOLS to the path of tla2tools.jar}
exec "$J" -XX:+UseParallelGC -cp "$T" tlc2.TLC -workers auto -metadir "${TMPDIR:-/tmp}/tlc-$1" "$@"
