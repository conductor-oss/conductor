---
description: "Building from Source — build and run the Conductor server and ui-next locally for development and testing."
---
# Building from source

Build and run the Conductor server and `ui-next` locally from source. The default configuration uses in-memory persistence with no indexing — all data is lost when the server stops. This setup is for development and testing only.

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
    | `http://localhost:8080/swagger-ui/index.html` | REST API docs |
    | `http://localhost:8080/api/` | API base URL |


## Running from a pre-compiled JAR

As an alternative to building from source, download and run the pre-compiled JAR:

```shell
export CONDUCTOR_VER=3.21.10
export REPO_URL=https://repo1.maven.org/maven2/org/conductoross/conductor-server
curl $REPO_URL/$CONDUCTOR_VER/conductor-core-$CONDUCTOR_VER-boot.jar \
  --output conductor-core-$CONDUCTOR_VER-boot.jar
java -jar conductor-core-$CONDUCTOR_VER-boot.jar
```


## Running ui-next from source

### Prerequisites

- A running Conductor server on port 8080
- Node.js 18+
- pnpm 10.x (activate the version pinned by `ui-next/package.json` with `corepack enable`)

### Steps

```shell
cd ui-next
corepack enable
pnpm install
```

Configure the backend URL in `.env` (the checked-in default targets a local server):

```shell
VITE_WF_SERVER=http://localhost:8080
```

Start the development server:

```shell
pnpm dev
```

The UI is accessible at [http://localhost:1234](http://localhost:1234). For runtime feature flags and authentication configuration, copy `public/context.js.example` to `public/context.js` and edit the copy.

To build compiled assets for production hosting:

```shell
pnpm build
```

The production build is written to `ui-next/dist/`.
