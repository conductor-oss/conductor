# Backend
Conductor provides a pluggable backend.  The current implementation uses Dynomite.

There are 4 interfaces that needs to be implemented for each backend:

```java
//Store for workfow and task definitions
com.netflix.conductor.dao.MetadataDAO
```

```java
//Store for workflow executions
com.netflix.conductor.dao.ExecutionDAO
```

```java
//Index for workflow executions
com.netflix.conductor.dao.IndexDAO
```

```java
//Queue provider for tasks
com.netflix.conductor.dao.QueueDAO
```

It is possible to mix and match different implementation for each of these.
e.g. SQS for queueing and a relational store for others.


# System Tasks
To create system tasks follow the steps below:

* Extend ```com.netflix.conductor.core.execution.tasks.WorkflowSystemTask```
* Instantiate the new classs as part of the statup (eager singleton)
