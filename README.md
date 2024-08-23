![Conductor OSS Logo](https://assets.conductor-oss.org/logo.png "Conductor OSS")


[![Github release](https://img.shields.io/github/v/release/conductor-oss/conductor.svg)](https://GitHub.com/Netflix/conductor-oss/releases)
[![License](https://img.shields.io/github/license/conductor-oss/conductor.svg)](http://www.apache.org/licenses/LICENSE-2.0)


Conductor is a platform _originally_ created at Netflix to orchestrate microservices and events.
Conductor OSS is maintained by the team of developers at [Orkes](https://orkes.io/) along with the members of the open source community.

The latest version is [![Github release](https://img.shields.io/github/v/release/conductor-oss/conductor.svg)](https://GitHub.com/conductor-oss/conductor/releases)
## Conductor OSS
This is the new home for the Conductor open source going forward (previously hosted at Netflix/Conductor).

_The last published version of Netflix Conductor will be **3.15.0** which we will continue to support._

If you would like to participate in the roadmap and development, [please reach out](https://forms.gle/P2i1xHrxPQLrjzTB7).

## ⭐ This repository
Show support for the Conductor OSS.  Please help spread the awareness by starring this repo.

[![GitHub stars](https://img.shields.io/github/stars/conductor-oss/conductor.svg?style=social&label=Star&maxAge=)](https://GitHub.com/conductor-oss/conductor/)

## Getting Started

### Using Docker (Recommended)
Follow the steps below to launch the docker container:

```shell
docker compose -f docker/docker-compose.yaml up
```
* Navigate to http://localhost:5000 once the container starts to launch UI.
* APIs are accessible at http://localhost:8080
* Swagger Docs:http://localhost:8080/swagger-ui/index.html?configUrl=/api-docs/swagger-config#/

## Database Requirements

* The default persistence used is Redis
* The indexing backend is [Elasticsearch](https://www.elastic.co/) (7.x)

## Configuration for various database backends

| Backend        | Configuration                                                                         |
|----------------|---------------------------------------------------------------------------------------|
| Redis + ES7    | [config-redis.properties](docker/server/config/config-redis.properties)               |
| Postgres       | [config-postgres.properties](docker/server/config/config-postgres.properties)         |
| Postgres + ES7 | [config-postgres-es7.properties](docker/server/config/config-postgres-es7.properties) |
| MySQL + ES7    | [config-mysql.properties](docker/server/config/config-mysql.properties)               |

## Other Requirements
* JDK 17+
* UI requires Node 14 to build. Earlier Node versions may work but are untested.

###  Building From Source
If you wish to build your own distribution, you can run ```./gradlew build``` from this project that products the runtime artifacts.
The runnable server is in server/ module.

## Conductor OSS Roadmap
[See the roadmap for the Conductor](ROADMAP.md)

## Resources
#### [Slack Community](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2hmxn0i3n-_W~a9rWMbvMoYmlJo3Y15g)
We have an active [community](https://join.slack.com/t/orkes-conductor/shared_invite/zt-2hmxn0i3n-_W~a9rWMbvMoYmlJo3Y15g) of Conductor users and contributors on the channel.
#### [Documentation Site](https://docs.conductor-oss.org/)
[Documentation](https://docs.conductor-oss.org/) and tutorial on how to use Conductor

[Discussion Forum](https://github.com/conductor-oss/conductor/discussions): Please use the forum for questions and discussing ideas and join the community.

### Conductor SDKs
Conductor supports creating workflows using JSON and Code.  
SDK support for creating workflows using code is available in multiple languages and can be found at https://github.com/conductor-sdk




## Get Support
There are several ways to get in touch with us:
* [Slack Community](https://join.slack.com/t/orkes-conductor/shared_invite/zt-xyxqyseb-YZ3hwwAgHJH97bsrYRnSZg)

## Contributors

<a href="https://github.com/conductor-oss/conductor/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=conductor-oss/conductor" />
</a>
