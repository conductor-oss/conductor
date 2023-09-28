
# Conductor Docker Builds

## Pre-built docker images

Conductor server with support for the following backend:
1. Redis
2. Postgres
3. Mysql
4. Cassandra

### Docker File for Server and UI

[Docker Image Source for Server with UI](docker/server/DockerFile)

### Configuration Guide for Conductor Server
Conductor uses a persistent store for managing state.  
The choice of backend is quite flexible and can be configured at runtime using `conductor.db.type` property.

Refer to the table below for various supported backend and required configurations to enable each of them.

> [!IMPORTANT]
> 
> See [config.properties](docker/server/config/config.properties) for the required properties for each of the backends.
>
> | Backend    | Property                           |
> |------------|------------------------------------|
> | postgres   | conductor.db.type=postgres         |
> | redis      | conductor.db.type=redis_standalone |
> | mysql      | conductor.db.type=mysql            |
> | cassandra  | conductor.db.type=cassandra        |    
>

Conductor using Elasticsearch for indexing the workflow data.  
Currently, Elasticsearch 6 and 7 are supported.

We welcome community contributions for other indexing backends.

**Note:** Docker images use Elasticsearch 7.

## Helm Charts
TODO: Link to the helm charts

## Run Docker Compose Locally
### Use the docker-compose to bring up the local conductor server.

| Docker Compose                                               | Description                |
|--------------------------------------------------------------|----------------------------|
| [docker-compose.yaml](docker-compose.yaml)                   | Redis + Elasticsearch 7    |
| [docker-compose-postgres.yaml](docker-compose-postgres.yaml) | Postgres + Elasticsearch 7 |
| [docker-compose-postgres.yaml](docker-compose-mysql.yaml)    | Mysql + Elasticsearch 7    |
