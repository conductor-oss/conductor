# Docker
## Conductor server
This Dockerfile create the conductor:server image

## Building the image
`docker build -t conductor:server .`

## Running the conductor server
 - Standalone server (interal DB): `docker run -d -t conductor:server`
 - Server (external DB required): `docker run -d -t -e "CONFIG_PROP=config.properties" conductor:server`
