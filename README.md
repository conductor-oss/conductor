![Conductor](docs/img/logo.svg)

[![Github release](https://img.shields.io/github/v/release/Netflix/conductor.svg)](https://GitHub.com/Netflix/conductor/releases)
[![License](https://img.shields.io/github/license/conductor-oss/conductor.svg)](http://www.apache.org/licenses/LICENSE-2.0)


Conductor is a platform _originally_ created at Netflix to orchestrate microservices and events.
Conductor OSS is maintained by the team of developers at [Orkes](https://orkes.io/) along with the members of the open source community.

## Conductor OSS
This is the new home for the Conductor open source going forward (previously hosted at Netflix/Conductor).
> [!IMPORTANT]  
> Going forward, all the bug fixes, feature requests and security patches will be applied and released from this repository.


The last published version of Netflix Conductor will be **3.15.0** which we will continue to support.

If you would like to participate in the roadmap and development, [please reach out](https://forms.gle/P2i1xHrxPQLrjzTB7).

## ⭐ This repository
Show support for the Conductor OSS.  Please help spread the awareness by starring this repo.

[![GitHub stars](https://img.shields.io/github/stars/conductor-oss/conductor.svg?style=social&label=Star&maxAge=)](https://GitHub.com/conductor-oss/conductor/)

## Update your local forks/clones
Please update your forks to point to this repo.  This will ensure your commits and PRs can be send against this repository
```shell
git remote set-url origin https://github.com/conductor-oss/conductor
```
> [!IMPORTANT]  
> **Follow the steps below if you have an active PR against the Netflix/Conductor repository**
> 1. Fork **this** repository
> 2. Update your local repository to change the remote to this repository
> 3. Send a PR against the `main` branch

## Releases
The latest version is [![Github release](https://img.shields.io/github/v/release/conductor-oss/conductor.svg)](https://GitHub.com/conductor-oss/conductor/releases)

The next scheduled release is in Feb 2024.

## Resources
#### [Slack Community](https://join.slack.com/t/orkes-conductor/shared_invite/zt-xyxqyseb-YZ3hwwAgHJH97bsrYRnSZg)
We have an active [community](https://join.slack.com/t/orkes-conductor/shared_invite/zt-xyxqyseb-YZ3hwwAgHJH97bsrYRnSZg) of Conductor users and contributors on the channel.
#### [Documentation Site](https://docs.conductor-oss.org/)
[Documentation](https://docs.conductor-oss.org/) and tutorial on how to use Conductor

[Discussion Forum](https://github.com/conductor-oss/conductor/discussions): Please use the forum for questions and discussing ideas and join the community.

### Conductor SDKs
Conductor supports creating workflows using JSON and Code.  
SDK support for creating workflows using code is available in multiple languages and can be found at https://github.com/conductor-sdk


## Getting Started - Building & Running Conductor

###  From Source:
If you wish to build your own distribution, you can run ```./gradlew build``` from this project that products the runtime artifacts.
The runnable server is in server/ module.

### Using Docker (Recommended)
Follow the steps below to launch the docker container:

```shell

# Create volumes for persistent stores
# Used to create a persistent volume that will preserve the 
docker volume create postgres
docker volume create redis

docker run --init -p 8080:8080 -p 1234:5000 --mount source=redis,target=/redis \
--mount source=postgres,target=/pgdata conductoross/conductor-standalone:3.15.0
```

Navigate to http://localhost:1234 once the container starts to launch UI.

## Docker Containers for production usage
```shell
docker pull conductoross/conductor:3.15.0
```


## Database Requirements

* The default persistence used is Redis
* The indexing backend is [Elasticsearch](https://www.elastic.co/) (6.x)

## Other Requirements
* JDK 17+
* UI requires Node 14 to build. Earlier Node versions may work but are untested.

## Get Support
There are several ways to get in touch with us:
* [Slack Community](https://join.slack.com/t/orkes-conductor/shared_invite/zt-xyxqyseb-YZ3hwwAgHJH97bsrYRnSZg)

## Contributors

<a href="https://github.com/conductor-oss/conductor/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=conductor-oss/conductor" />
</a>
