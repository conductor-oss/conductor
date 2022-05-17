
# Running via Docker Compose

In this article we will explore how you can set up Netflix Conductor on your local machine using Docker compose.
The docker compose will bring up the following:
1. Conductor API Server
2. Conductor UI
3. Elasticsearch for searching workflows

## Prerequisites
1. Docker: [https://docs.docker.com/get-docker/](https://docs.docker.com/get-docker/)
2. Recommended host with CPU and RAM to be able to run multiple docker containers (at-least 16GB RAM)

## Steps

#### 1. Clone the Conductor Code

```shell
$ git clone https://github.com/Netflix/conductor.git
```

#### 2. Build the Docker Compose

```shell
$ cd conductor
conductor $ cd docker
docker $ docker-compose build
```
#### Note: Conductor supplies multiple docker compose templates that can be used with different configurations:

| File                           | Containers                                                                              |
|--------------------------------|-----------------------------------------------------------------------------------------|
| docker-compose.yaml            | 1. In Memory Conductor Server 2. Elasticsearch 3. UI                                    |
| docker-compose-dynomite.yaml   | 1. In Memory Conductor Server 2. Elasticsearch 3. UI 4. Dynomite Redis for persistence  |
| docker-compose-postgres.yaml   | 1. In Memory Conductor Server 2. Elasticsearch 3. UI 4. Postgres persistence            |
| docker-compose-prometheus.yaml | Brings up Prometheus server                                                             |    

#### 3. Run Docker Compose

```shell
docker $ docker-compose up
```

Once up and running, you will see the following in your Docker dashboard:

1. Elasticsearch
2. Conductor UI
3. Conductor Server

You can access all three on your browser to verify that it is running correctly:

Conductor Server URL: [http://localhost:8080/swagger-ui/index.html?configUrl=/api-docs/swagger-config](http://localhost:8080/swagger-ui/index.html?configUrl=/api-docs/swagger-config)

<img src="/img/tutorial/swagger.png" style="width: 100%"/>

Conductor UI URL: [http://localhost:5000/](http://localhost:5000/)

<img src="/img/tutorial/conductorUI.png" style="width: 100%" />


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

#### Not enough memory

    1. You will need at least 16 GB of memory to run everything. You can modify the docker compose to skip using
       Elasticsearch if you have no option to run this with your memory options.
    2. To disable Elasticsearch using Docker Compose - follow the steps listed here: **TODO LINK**

#### Elasticsearch fails to come up in arm64 based CPU machines

    1. As of writing this article, Conductor relies on 6.8.x version of Elasticsearch. This version doesn't have an
       arm64 based Docker image. You will need to use Elasticsearch 7.x which requires a bit of customization to get up
       and running 

####  Elasticsearch remains in Yellow health

    1. When you run Elasticsearch, sometimes the health remains in Yellow state. Conductor server by default requires
       Green state to run when indexing is enabled. To work around this, you can use the following property: 
       `conductor.elasticsearch.clusteHealthColor=yellow` Reference: [Issue 2262](https://github.com/Netflix/conductor/issues/2262)



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

