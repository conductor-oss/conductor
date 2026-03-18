#!/usr/bin/env bash
# Serves the Conductor documentation site locally using MkDocs
# Usage: ./serve-docs.sh [port]

PORT="${1:-8000}"

echo "Starting Conductor docs at http://localhost:${PORT}"
echo "Press Ctrl+C to stop"

mkdocs serve -a "localhost:${PORT}"
