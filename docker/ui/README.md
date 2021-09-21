# Docker
## Conductor UI
This Dockerfile create the conductor:ui image

## Building the image

Run the following commands from the project root.

`docker build -f docker/ui/Dockerfile -t conductor:ui .`

## Running the conductor server
 - With localhost conductor server: `docker run -p 5000:5000 -d -t conductor:ui`
 - With external conductor server: `docker run -p 5000:5000 -d -t -e "WF_SERVER=http://conductor-server:8080" conductor:ui`
