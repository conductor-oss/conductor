---
description: "Building from Source — build and run the Conductor server and UI locally from source for development and testing."
---
# Building from source

Build and run Conductor server and UI locally from source. The default configuration uses in-memory persistence with no indexing — all data is lost when the server stops. This setup is for development and testing only.

For persistent backends, use [Docker Compose](deploy.md) or configure a database backend.


## Prerequisites

- Java (JDK) 21+
- (Optional) [Docker](https://www.docker.com/get-started/) for running tests


## Building and running the server

1. Clone the repository:

    ```shell
    git clone https://github.com/conductor-oss/conductor.git
    cd conductor
    ```

2. Run with Gradle:

    ```shell
    cd server
    ../gradlew bootRun
    ```

    To use a custom configuration file:

    ```shell
    CONFIG_PROP=config.properties ../gradlew bootRun
    ```

3. The server is now running:

    | URL | Description |
    |:----|:---|
    | `http://localhost:8080` | Conductor UI |
    | `http://localhost:8080/swagger-ui/index.html` | REST API docs |
    | `http://localhost:8080/api/` | API base URL |


## Running server-lite locally with the full UI

Use `server-lite` when you want a local source build for development or smoke testing without starting Redis, Postgres, MySQL, Cassandra, Elasticsearch, or OpenSearch. The module is configured in `server-lite/src/main/resources/application.properties` to use SQLite for persistence, queueing, and indexing.

The `server-lite` backend serves whatever static assets are present under `server-lite/src/main/resources/static`. If the React UI has not been built and copied there, the root page can show only a minimal Conductor landing page with links to Swagger and the user guide. Build and copy the UI assets before starting the server when you want the full Conductor UI.

### Prerequisites

- Java 21+
- Node.js 14.17+
- Network access for the first Gradle and UI dependency download

### Build the UI assets

From the repository root:

```shell
cd ui
npx yarn@1.22.22 install --frozen-lockfile
REACT_APP_ENABLE_ERRORS_INSPECTOR=true npx yarn@1.22.22 build
cd ..
mkdir -p server-lite/src/main/resources/static
cp -R ui/build/. server-lite/src/main/resources/static/.
```

### Start server-lite

```shell
./gradlew :conductor-server-lite:bootRun
```

If your shell points to an older Java runtime, set `JAVA_HOME` for the command:

```shell
JAVA_HOME=/path/to/java-21-home ./gradlew :conductor-server-lite:bootRun
```

The server starts on port `7001` and stores local state in `server-lite/conductoross.db`.

### Verify the local server

```shell
curl -sS http://localhost:7001/actuator/health
```

Expected stable response fields:

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "SQLite"
      }
    }
  }
}
```

Open the UI at [http://localhost:7001](http://localhost:7001). A built UI page has the title `Conductor UI` and loads static assets from `/static/js/` and `/static/css/`.

Other useful local URLs:

| URL | Description |
|:----|:---|
| `http://localhost:7001` | Conductor UI |
| `http://localhost:7001/swagger-ui/index.html` | REST API docs |
| `http://localhost:7001/api/` | API base URL |


## Running from a pre-compiled JAR

As an alternative to building from source, download and run the pre-compiled JAR:

```shell
export CONDUCTOR_VER=3.21.10
export REPO_URL=https://repo1.maven.org/maven2/org/conductoross/conductor-server
curl $REPO_URL/$CONDUCTOR_VER/conductor-core-$CONDUCTOR_VER-boot.jar \
  --output conductor-core-$CONDUCTOR_VER-boot.jar
java -jar conductor-core-$CONDUCTOR_VER-boot.jar
```


## Running the UI from source

### Prerequisites

- A running Conductor server on port 8080
- [Node.js](https://nodejs.org) v18+
- [Yarn](https://classic.yarnpkg.com/en/docs/install)

### Steps

```shell
cd ui
yarn install
yarn run start
```

The UI is accessible at [http://localhost:5000](http://localhost:5000).

To build compiled assets for production hosting:

```shell
yarn build
```
