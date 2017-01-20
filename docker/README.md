# Docker

This dockerfile runs using dynomite and elasticsearch

##Building the image
Building the image:
`docker build -t conductor .`

or using compose:
`docker-compose build`

## Running conductor
Running the image:
`docker run -d -t conductor`
(requires elasticsearch running locally)

Using compose:
`docker-compose up`

## Exiting Compose
`ctrl+c` will exit docker compose

To ensure images are stopped do
`docker-compose down`

## Running in Interactive Mode 
In interactive mode the default startup script for the container do not run
`docker run -t -i conductor -`

