# Docker
## Conductor server and UI
This Dockerfile create the conductor:serverAndUI image

## Building the image
`docker build -t conductor:serverAndUI .`

## Running the conductor server
 - Standalone server (interal DB): `docker run -d -t conductor:serverAndUI`
 - Server (external DB required): `docker run -d -t -e "CONFIG_PROP=config.properties" conductor:serverAndUI`
