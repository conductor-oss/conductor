### gRPC Framework
As part of this addition, all of the modules and bootstrap code within them were refactored to leverage providers, which facilitated moving  the Jetty server into a separate module and the conformance to Guice guidelines and best practices. 
This feature constitutes a server-side gRPC implementation along with protobuf RPC schemas for the workflow, metadata and task APIs that can be run concurrently with the Jersey-based HTTP/REST server. The protobuf models for all the types are exposed through the API. gRPC java clients for the workflow, metadata and task APIs are also available for use. Another valuable addition is an idiomatic Go gRPC client implementation for the worker API.
The proto models are auto-generated at compile time using this ProtoGen library. This custom library adds messageInput and messageOutput fields to all proto tasks and task definitions. The goal of these fields is providing a type-safe way to pass input and input metadata through tasks that use the gRPC API. These fields use the Any protobuf type which can store any arbitrary message type in a type-safe way, without the server needing to know the exact serialization format of the message. In order to expose these Any objects in the REST API, a custom encoding is used that contains the raw data of the serialized message by converting it into a dictionary with '@type' and '@value' keys, where '@type' is identical to the canonical representation and '@value' contains a base64 encoded string with the binary data of the serialized message. The JsonMapperProvider provides the object mapper initialized with this module to enable serialization/deserialization of these JSON objects.


### Cassandra Persistence
The Cassandra persistence layer currently provides a partial implementation of the ExecutionDAO that supports all the CRUD operations for tasks and workflow execution. The data modelling is done in a denormalized manner and stored in two tables. The “workflows” table houses all the information for a workflow execution including all its tasks and is the source of truth for all the information regarding a workflow and its tasks. The “task_lookup” table, as the name suggests stores a lookup of taskIds to workflowId. This table facilitates the fast retrieval of task data given a taskId. 
All the datastore operations that are used during the critical execution path of a workflow have been implemented currently. Few of the operational abilities of the ExecutionDAO are yet to be implemented. This module also does not provide implementations for QueueDAO and MetadataDAO. We envision using the Cassandra DAO with an external queue implementation, since implementing a queuing recipe on top of Cassandra is an anti-pattern that we want to stay away from.


### External Payload Storage
The implementation of this feature is such that the externalization of payloads is fully transparent and automated to the user. Conductor operators can configure the usage of this feature and is completely abstracted and hidden from the user, thereby allowing the operators full control over the barrier limits. Currently, only AWS S3 is supported as a storage system, however, as with all other Conductor components, this is pluggable and can be extended to enable any other object store to be used as an external payload storage system.
The externalization of payloads is enforced using two kinds of [barriers](../externalpayloadstorage). Soft barriers are used when the  payload size is warranted enough to be stored as part of workflow execution. These payloads will be stored in external storage and used during execution. Hard barriers are enforced to safeguard against voluminous data, and such payloads are rejected and the workflow execution is failed.
The payload size is evaluated in the client before being sent over the wire to the server. If the payload size exceeds the configured soft limit, the client makes a request to the server for the location at which the payload is to be stored. In this case where S3 is being used, the server returns a signed url for the location and the client uploads the payload using this signed url. The relative path to the payload object is then stored in the workflow/task metadata. The server can then download this payload from this path and use as needed during execution. This allows the server to control access to the S3 bucket, thereby making the user applications where the worker processes are run completely agnostic of the permissions needed to access this location.


### Dynamic Workflow Executions
In the earlier version (v1.x), Conductor allowed the execution of workflows referencing the workflow and task definitions stored as metadata in the system. This meant that a workflow execution with 10 custom tasks to run entailed:

- Registration of the 10 task definitions if they don't exist (assuming workflow task type SIMPLE for simplicity)
- Registration of the workflow definition
- Each time a definition needs to be retrieved, a call to the metadata store needed to be performed
- In addition to that, the system allowed current metadata that is in use to be altered, leading to possible inconsistencies/race conditions

To eliminate these pain points, the execution was changed such that the workflow definition is embedded within the workflow execution and the task definitions are themselves embedded within this workflow definition. This enables the concept of ephemeral/dynamic workflows and tasks. Instead of fetching metadata definitions throughout the execution, the definitions are fetched and embedded into the execution at the start of the workflow execution. This also enabled the StartWorkflowRequest to be extended to provide the complete workflow definition that will be used during execution, thus removing the need for pre-registration. The MetadataMapperService prefetches the workflow and task definitions and embeds these within the workflow data, if not provided in the StartWorkflowRequest.

Following benefits are seen as a result of these changes:

- Grants immutability of the definition stored within the execution data against modifications to the metadata store
- Better testability of workflows with faster experimental changes to definitions
- Reduced stress on the datastore due to prefetching the metadata only once at the start


### Decoupling Elasticsearch from Persistence
In the earlier version (1.x), the indexing logic was imbibed within the persistence layer, thus creating a tight coupling between the primary datastore and the indexing engine. This meant that the primary datastore determines how we orchestrate between the storage (redis, mysql, etc) and the indexer(elastic search). The main disadvantage of this approach is the lack of flexibility, that is, we cannot run an in-memory database and external elastic search or vice-versa.
We plan to improve this further by removing the indexing from the critical path of workflow execution, thus reducing possible points of failure during execution.


### Elasticsearch 5/6 Support
Indexing workflow execution is one of the primary features of Conductor. This enables archival of terminal state workflows from the primary data store, along with providing a clean search capability from the UI. 
In Conductor 1.x, we supported both versions 2 and 5 of Elasticsearch by shadowing version 5 and all its dependencies. This proved to be rather tedious increasing build times by over 10 minutes. In Conductor 2.x, we have removed active support for ES 2.x, because of valuable community contributions for elasticsearch 5 and elasticsearch 6 modules. Unlike Conductor 1.x, Conductor 2.x supports elasticsearch 5 by default, which can easily be replaced with version 6 by following the simple instructions [here](https://github.com/Netflix/conductor/tree/master/es6-persistence#build).

### Maintaining workflow consistency with distributed locking and fencing tokens

#### Problem

Conductor’s Workflow decide is the core logic which recursively evaluates the state of the workflow, schedules tasks, persists workflow and task(s) state at several checkpoints, and progresses the workflow.
 
In a multi-node Conductor server deployment, the decide on a workflow can be triggered concurrently. For example, the worker can update Conductor server with latest task state, which calls decide, while the sweeper service (which periodically evaluates the workflow state to progress from task timeouts) would also call the decide on a different instance. The decide can be run concurrently in two different jvm nodes with two different workflow states, and based on the workflow configuration and current state, the result could be inconsistent.

#### A two-part solution to maintain Workflow Consistency

**Preventing concurrent decides with distributed locking:**
The goal is to allow only one decide to run on a workflow at any given time across the whole Conductor Server cluster. This can be achieved by plugging in distributed locking implementations like Zookeeper, Redlock etc. A Zookeeper module implementing Conductor’s Locking service is provided.

**Preventing stale data updates with fencing tokens:**
While the locking service helps to run one decide at a time, it might still be possible for nodes with timed out locks to reactivate and continue execution from where it left off (usually with stale data). This can be avoided with fencing tokens, which basically is an incrementing counter on workflow state with read-before-write support in a transaction or similar construct.

*At Netflix, we use Cassandra. Considering the tradeoffs of Cassandra’s Lightweight Transactions (LWT) and the probability of this stale updates happening, and our testing results, we’ve decided to first only rollout distributed locking with Zookeeper. We'll monitor our system and add C* LWT if needed.

#### Setting up desired level of consistency

Based on your requirements, it is possible to use none, one or both of the distributed locking and fencing tokens implementations.

#### Alternative solution to distributed "decide" evaluation

As mentioned in the previous section, the "decide" logic is triggered from multiple places in a conductor instance. Either a direct trigger such as user starting a workflow or a timed trigger from the Sweeper service.

> Sweeper service is responsible for continually checking state of all workflows executions and trigger the "decide" logic which in turn can time the workflow out.

In a single node deployment (single dynomite rack and single conductor server) this shouldn't be a problem. But when running multiple replicated dynomite racks and a conductor server on top of each rack, this might trigger the race condition described in previous section.

> Dynomite rack is a single or multiple instance dynomite setup that holds all the data.

> More on dynomite HA setup: (https://netflixtechblog.com/introducing-dynomite-making-non-distributed-databases-distributed-c7bce3d89404)

In a cluster deployment, the default behavior for Dyno Queues is such, that it distributes the workload (round-robin style) to all the conductor servers.
This can create a situation where the first task to be executed is queued for conductor server #1 but the sweeper service is queued for conductor server #2.

##### More on dyno queues

Dyno queues are the default queuing mechanism of conductor.

Queues are allocated and used for:
* Task execution - each task type gets a queue
* Workflow execution - single queue with all currently executing workflows (deciderQueue)
  * This queue is used by SweeperService

**Each conductor server instance gets its own set of queues**. Or more precisely a queue shard of its own.
This means that if you have 2 task types, you end up with 6 queues altogether e.g.

```
conductor_queues.test.QUEUE._deciderQueue.c
conductor_queues.test.QUEUE._deciderQueue.d
conductor_queues.test.QUEUE.HTTP.c
conductor_queues.test.QUEUE.HTTP.d
conductor_queues.test.QUEUE.LAMBDA.c
conductor_queues.test.QUEUE.LAMBDA.d
```

> The "c" and "d" suffixes are the shards identifying conductor server instace #1 and instance #2 respectively.

> The shard names are extracted from dynomite rack name such as us-east-1c that is set in "LOCAL_RACK" or "EC2_AVAILABILTY_ZONE"

Considering an execution of a simple workflow with just 2 tasks: [HTTP, LAMBDA], you should end up with queues being filled as follows:

```
Workflow execution    -> conductor_queues.test.QUEUE._deciderQueue.c
HTTP taks execution   -> conductor_queues.test.QUEUE.HTTP.d
LAMBDA task execution -> conductor_queues.test.QUEUE.LAMBDA.c
```

Which means that SweeperService in conductor instance #1 is responsible for sweeping the workflow, conductor #2 is responsible for executing HTTP task and conductor #1 again responsible for executing LAMBDA task.

This illustrates the race condition: If the HTTP task completion in instance #2 happens at the same time as sweep in instance #1 ... you can end up with 2 different updates to a workflow execution: one update timing workflow out while the other completing the task and scheduling next.

> The round-robin strategy responsible for work distribution is defined [here](https://github.com/Netflix/dyno-queues/blob/1cde55bbb69acd631c671a0cb2f9db2419163e33/dyno-queues-redis/src/main/java/com/netflix/dyno/queues/redis/sharding/RoundRobinStrategy.java)

##### Back to alternative solution

The alternative solution here is **Switching round-robin queue allocation for a local-only strategy**.
Meaning that a workflow and its task executions are queued only for the conductor instance which started the workflow.

This completely avoids the race condition for the price of removing task execution distribution.

Since all tasks and the sweeper service read/write only from/to "local" queues, it is impossible to run into a race condition between conductor instances.

The downside here is that the workload is not distributed across all conductor servers. Which might be an advantage in active-standby deployments.

Considering other downsides ...

Considering a situation where a conductor instance goes down:
* With local-only strategy, the workflow executions from failed conductor instance will not progress until:
  * The conductor instance is restarted or
  * The executions are manually terminated and restarted from a different node
* With round-robin strategy, there is a chance the tasks will be rescheduled on a different conductor node
  * This is nondeterministic though
  
**Enabling local only queue allocation strategy for dyno queues:**

Just enable following setting the config.properties:

```
workflow.dyno.queue.sharding.strategy=localOnly
```

> The default is roundRobin