# Building Conductor From Source

Learn how you can set up the Conductor server and UI on your local machine by building it from source. 

When building from source, the default configuration comes with in-memory persistence and no indexing. This means all data will be wiped once the server is terminated, and the search functionality in the UI will not work. This set-up is useful for testing or demo only.

You can [run Conductor using Docker](docker.md) to download Conductor with persistence and indexing already configured.


## Building Conductor server from source

> [!NOTE] 
> "Prerequisites"
>  * Java (JDK) v17
>  * (Optional) [Docker](https://www.docker.com/get-started/) for running tests

**To build Conductor server from source:**

1. Clone the Conductor repository.

    ```shell
    $ git clone https://github.com/conductor-oss/conductor.git
    ```

2. (For Mac users) If you are using a new Mac with an Apple silicon chip, you must modify the `conductor/grpc/build.gradle` file by adding "osx-x86_64" to the following plugin:
    ```
    protobuf {
        plugins {
            grpc {
                artifact = "io.grpc:protoc-gen-grpc-java:${revGrpc}:osx-x86_64"
            }
        }
    ...
    }
    ```

3. (For Mac users) If you are using a new Mac with an Apple silicon chip, you may also need to install Rosetta:

    ```shell
    softwareupdate --install-rosetta
    ```

4. Run Conductor with Gradle.

    ```shell
    $ cd conductor/server
    server $ ../gradlew bootRun
    ```

    To run Conductor, with a specific configuration file, specify `CONFIG_PROP`.

    ```shell
    # Ensure all other services have been started before running the server
    server $ CONFIG_PROP=config.properties ../gradlew bootRun
    ```


The API documentation should now be accessible at [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html).

![swagger](swagger.png)

### Running Conductor server from a pre-compiled JAR

As an alternative to building from source, you can download and run the pre-compiled JAR.

```shell
export CONDUCTOR_VER=3.21.10
export REPO_URL=https://repo1.maven.org/maven2/org/conductoross/conductor-server
curl $REPO_URL/$CONDUCTOR_VER/conductor-core-$CONDUCTOR_VER-boot.jar \
--output conductor-core-$CONDUCTOR_VER-boot.jar; java -jar conductor-core-$CONDUCTOR_VER-boot.jar
```

The API documentation should now be accessible at [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html).


## Running Conductor UI from source

> [!NOTE]
> "Prerequisites"
> * A running Conductor server on port 8080
> * [Node v14](https://nodejs.org) for the UI to build
> * [Yarn](https://classic.yarnpkg.com/en/docs/install) for building and running the UI

The UI is a standard `create-react-app` React single page application (SPA).

**To run Conductor UI from source:**

1. Run `yarn install` from the `/ui` directory to retrieve package dependencies.

    ```shell
    $ cd conductor/ui
    ui $ yarn install
    ```

2. Run the UI on the bundled development server using `yarn run start`.

    ```shell
    ui $ yarn run start
    ```

The UI should now be accessible at [http://localhost:5000](http://localhost:5000).

![conductor ui](conductorUI.png)


> [!NOTE]
> To use the UI locally, there is no need to build the project. If you require compiled assets to host on a production web server, you can build the project using `yarn build`.

