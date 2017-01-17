#!/bin/bash

# Run the conductor server
java -jar conductor-server-*-all.jar config.properties &

# Run the conductor UI
cd /ui
npm install
npm run watch


