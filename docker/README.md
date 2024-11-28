
# Conductor Docker Builds

## Pre-built docker images

Conductor server with support for the following backend:
1. Redis
2. Postgres
3. Mysql
4. Cassandra
5. Oracle

### Docker File for Server and UI

[Docker Image Source for Server with UI](server/Dockerfile)

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
> | oracle     | conductor.db.type=oracle           |   
>

Conductor using Elasticsearch for indexing the workflow data.  
Currently, Elasticsearch 7 is supported.

We welcome community contributions for other indexing backends.

**Note:** Docker images use Elasticsearch 7.

## Helm Charts
TODO: Link to the helm charts

## Run Docker Compose Locally
### Use the docker-compose to bring up the local conductor server.

| Docker Compose                                                   | Description                |
|------------------------------------------------------------------|----------------------------|
| [docker-compose.yaml](docker-compose.yaml)                       | Redis + Elasticsearch 7    |
| [docker-compose-oracle-es7.yaml](docker-compose-oracle-es7.yaml) | oracle + Elasticsearch 7   |

### Network errors during UI build with yarn

It has been observed, that the UI build may fail with an error message like

```
> [linux/arm64 ui-builder 5/7] RUN yarn install && cp -r node_modules/monaco-editor public/ && yarn build:
269.9     at Object.onceWrapper (node:events:633:28)
269.9     at TLSSocket.emit (node:events:531:35)
269.9     at Socket._onTimeout (node:net:590:8)
269.9     at listOnTimeout (node:internal/timers:573:17)
269.9     at process.processTimers (node:internal/timers:514:7)
269.9 info Visit https://yarnpkg.com/en/docs/cli/install for documentation about this command.
281.2 info There appears to be trouble with your network connection. Retrying...
313.5 info There appears to be trouble with your network connection. Retrying...
920.3 info There appears to be trouble with your network connection. Retrying...
953.6 info There appears to be trouble with your network connection. Retrying...
```

This does not necessarily mean, that the network is unavailable, but can be caused by too high latency, as well. `yarn` accepts the option `--network-timeout <#ms>` to set a custom timeout in milliseconds.

For passing arguments to `yarn`, in [this Dockerfile](server/Dockerfile) the _optional_ build arg `YARN_OPTS` has been added. This argument will be added to each `yarn` call.

When using one of the `docker-compose-*` files, you can set this via the environment variable `YARN_OPTS`, e.g.:

```
YARN_OPTS='--network-timeout 10000000' docker compose -f docker-compose.yaml up
```

When building a Docker image using `docker`, you must call it like e.g.

```
docker build --build-arg='YARN_OPTS=--network-timeout 10000000' .. -f server/Dockerfile -t oss-conductor:v3.21.9
```
