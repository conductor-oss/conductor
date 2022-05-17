# Community projects related to Conductor

## Client SDKs

Further, all of the (non-Java) SDKs have a new GitHub home: the Conductor SDK repository is your new source for Conductor SDKs:

* [Golang](https://github.com/conductor-sdk/conductor-go)
* [Python](https://github.com/conductor-sdk/conductor-python)
* [C#](https://github.com/conductor-sdk/conductor-csharp)
* [Clojure](https://github.com/conductor-sdk/conductor-clojure)

All contributions on the above client sdks can be made on [Conductor SDK](https://github.com/conductor-sdk) repository.

## Microservices operations

* https://github.com/flaviostutz/schellar - Schellar is a scheduler tool for instantiating Conductor workflows from time to time, mostly like a cron job, but with transport of input/output variables between calls.

* https://github.com/flaviostutz/backtor - Backtor is a backup scheduler tool that uses Conductor workers to handle backup operations and decide when to expire backups (ex.: keep backup 3 days, 2 weeks, 2 months, 1 semester)

* https://github.com/cquon/conductor-tools - Conductor CLI for launching workflows, polling tasks, listing running tasks etc


## Conductor deployment

* https://github.com/flaviostutz/conductor-server - Docker container for running Conductor with  Prometheus metrics plugin installed and some tweaks to ease provisioning of workflows from json files embedded to the container

* https://github.com/flaviostutz/conductor-ui - Docker container for running Conductor UI so that you can easily scale UI independently

* https://github.com/flaviostutz/elasticblast - "Elasticsearch to Bleve" bridge tailored for running Conductor on top of Bleve indexer. The footprint of Elasticsearch may cost too much for small deployments on Cloud environment.

* https://github.com/mohelsaka/conductor-prometheus-metrics - Conductor plugin for exposing Prometheus metrics over path '/metrics'

## OAuth2.0 Security Configuration
Forked Repository - [Conductor (Secure)](https://github.com/maheshyaddanapudi/conductor/tree/oauth2)

[OAuth2.0 Role Based Security!](https://github.com/maheshyaddanapudi/conductor/blob/oauth2/SECURITY.md) - Spring Security with easy configuration to secure the Conductor server APIs.

Docker image published to [Docker Hub](https://hub.docker.com/repository/docker/conductorboot/server)

## Conductor Worker utilities

* https://github.com/ggrcha/conductor-go-client - Conductor Golang client for writing Workers in Golang

* https://github.com/courosh12/conductor-dotnet-client - Conductor DOTNET client for writing Workers in DOTNET
  * https://github.com/TwoUnderscorez/serilog-sinks-conductor-task-log - Serilog sink for sending worker log events to Netflix Conductor

* https://github.com/davidwadden/conductor-workers - Various ready made Conductor workers for common operations on some platforms (ex.: Jira, Github, Concourse)

## Conductor Web UI

* https://github.com/maheshyaddanapudi/conductor-ng-ui - Angular based - Conductor Workflow Management UI

## Conductor Persistence

### Mongo Persistence

* https://github.com/maheshyaddanapudi/conductor/tree/mongo_persistence - With option to use Mongo Database as persistence unit.
  * Mongo Persistence / Option to use Mongo Database as persistence unit.
  * Docker Compose example with MongoDB Container.

### Oracle Persistence

* https://github.com/maheshyaddanapudi/conductor/tree/oracle_persistence - With option to use Oracle Database as persistence unit.
  * Oracle Persistence / Option to use Oracle Database as persistence unit : version > 12.2 - Tested well with 19C
  * Docker Compose example with Oracle Container.

## Schedule Conductor Workflow
* https://github.com/jas34/scheduledwf - It solves the following problem statements:
	* At times there are use cases in which we need to run some tasks/jobs only at a scheduled time.
	* In microservice architecture maintaining schedulers in various microservices is a pain.
	* We should have a central dedicate service that can do scheduling for us and provide a trigger to a microservices at expected time.
* It offers an additional module `io.github.jas34.scheduledwf.config.ScheduledWfServerModule` built on the existing core 
of conductor and does not require deployment of any additional service.
For more details refer: [Schedule Conductor Workflows](https://jas34.github.io/scheduledwf) and [Capability In Conductor To Schedule Workflows](https://github.com/Netflix/conductor/discussions/2256)