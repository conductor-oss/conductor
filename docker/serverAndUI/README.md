# Docker
## Conductor server and UI
This Dockerfile create the conductor:serverAndUI image

## Building the image
`docker build -t conductor:serverAndUI .`

## Running the conductor server
 - Standalone server (interal DB): `docker run -p 8080:8080 -p 5000:5000 -d -t conductor:serverAndUI`
 - Server (external DB required): `docker run -p 8080:8080 -p 5000:5000 -d -t -e "CONFIG_PROP=config.properties" conductor:serverAndUI`
