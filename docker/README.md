# Docker
## Getting Started with Docker Compose
The easiest way to start experimenting with Conductor is via `docker-compose`.
```
cd docker
docker-compose build
docker-compose up
```

This default docker compose build establishes 3 services each running in its own container
* Elasticsearch
* Conductor Server
* Conductor UI

The UI can be accessed by pointing your browser to `http://localhost:5000/`
The Server API is accessible at `http://localhost:8080/`

### Alternative Persistence Engines
By default `docker-compose.yaml` uses `config-local.properties`. This configures the `memory` database, where data is lost when the server terminates. This configuration is useful for testing or demo only.

A selection of `docker-compose-*.yaml` and `config-*.properties` files are provided demonstrating the use of alternative persistence engines.

For example this will start the server instance backed by a PostgreSQL DB.
```
docker-compose -f docker-compose.yaml -f docker-compose-postgres.yaml up
```

### Exiting Compose
`Ctrl+c` will exit docker compose.

To ensure images are stopped execute: `docker-compose down`.

## Standalone Server Image
To build and run the server image, without using `docker-compose`, from the `docker` directory execute:
```
docker build -t conductor:server -f server/Dockerfile ../
docker run -p 8080:8080 -d --name conductor_server conductor:server
```
This builds the image `conductor:server` and runs it in a container named `conductor_server`. The API should now be accessible at `localhost:8080`.

To 'login' to the running container, use the command:
```
docker exec -it conductor_server /bin/sh
```

## Standalone UI Image
From the `docker` directory, 
```
docker build -t conductor:ui -f ui/Dockerfile ../
docker run -p 5000:5000 -d --name conductor_ui conductor:ui
```
This builds the image `conductor:ui` and runs it in a container named `conductor_ui`. The UI should now be accessible at `localhost:5000`.

### Note
* In order for the UI to do anything useful the Conductor Server must already be running on port 8080, either in a Docker container (see above), or running directly in the local JRE.
* Additionally, significant parts of the UI will not be functional without Elastisearch being available. Using the `docker-compose` approach alleviates these considerations.

## Monitoring with Prometheus

Start Prometheus with:
`docker-compose -f docker-compose-prometheus.yaml up -d`

Go to [http://127.0.0.1:9090](http://127.0.0.1:9090).


## Potential problem when using Docker Images

#### Elasticsearch timeout
Standalone(single node) elasticsearch has a yellow status which will cause timeout for conductor server (Required: Green).
Spin up a cluster (more than one) to prevent timeout or use config option `conductor.elasticsearch.clusteHealthColor=yellow`.

See issue: https://github.com/Netflix/conductor/issues/2262

#### Changes in config-*.properties do not take effect
Config is copy into image during docker build. You have to rebuild the image or better, link a volume to it to reflect new changes.

#### To troubleshoot a failed startup
Check the log of the server, which is located at `/app/logs` (default directory in dockerfile)

#### Unable to access to conductor:server API on port 8080
It may takes some time for conductor server to start. Please check server log for potential error.

#### Elasticsearch
Elasticsearch is optional, please be aware that disable it will make most of the conductor UI not functional.

##### How to enable Elasticsearch
* Set `workflow.indexing.enabled=true` in your_config.properties
* Add config related to elasticsearch
  E.g.: `conductor.elasticsearch.url=http://es:9200`

##### How to disable Elasticsearch
* Set `workflow.indexing.enabled=false` in your_config.properties
* Comment out all the config related to elasticsearch
E.g.: `conductor.elasticsearch.url=http://es:9200`


