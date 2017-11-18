# Docker

This dockerfile runs using dynomite and elasticsearch. The conductor is split into the backend (server) and the frontend (ui). If an image with both of these items combined is desired, build the Dockerfile in the folder serverAndUI

## Building the image
Dependency (build the jar files in ./server from project root)
- `gradlew build`

Building the images:
 - `docker build -t conductor:server ./server`
 - `docker build -t conductor:ui ./ui`

or using compose:
 - `docker-compose build`

This builds the images:
 - conductor:server - the conductor server and API.
 - conductor:ui - the conductor frontend

## Running conductor
Running the images:
 - `docker run -p 8080:8080 -d -t conductor:server`
 - `docker run -p 5000:5000 -d -t conductor:ui` (requires elasticsearch running locally)

Using compose:
`docker-compose up`

## Exiting Compose
`ctrl+c` will exit docker compose.


To ensure images are stopped do:
 - `docker-compose down`

## Running in Interactive Mode
In interactive mode the default startup script for the container do not run
 - `docker run -p 8080:8080 -t -i conductor:server -`
 - `docker run -p 5000:5000 -t -i conductor:ui -`
