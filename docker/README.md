# Docker

This dockerfile runs using an in-memory database and elasticsearch

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



