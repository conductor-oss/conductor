# Overview

![Architecture diagram](/img/conductor-architecture.png)

The API and storage layers are pluggable and provide ability to work with different backends and queue service providers.

## Runtime Model
Conductor follows RPC based communication model where workers are running on a separate machine from the server. Workers communicate with server over HTTP based endpoints and employs polling model for managing work queues.

![Runtime Model of Conductor](/img/overview.png)

**Notes**

* Workers are remote systems that communicate over HTTP with the conductor servers.
* Task Queues are used to schedule tasks for workers.  We use [dyno-queues][1] internally but it can easily be swapped with SQS or similar pub-sub mechanism.
* conductor-redis-persistence module uses [Dynomite][2] for storing the state and metadata along with [Elasticsearch][3] for indexing backend.
* See section under extending backend for implementing support for different databases for storage and indexing.

[1]: https://github.com/Netflix/dyno-queues
[2]: https://github.com/Netflix/dynomite
[3]: https://www.elastic.co
