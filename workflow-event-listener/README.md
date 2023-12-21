# Workflow Event Listeners
Workflow Event listeners can be configured for the purpose in Conductor:
1. Remove and/or archive workflows from primary datasource (e.g. Redis) once the workflow reaches a terminal status
2. Publish a message to a conductor queue as the workflows complete that can be used to trigger other workflows 

## Published Artifacts

Group: `com.netflix.conductor`

| Published Artifact | Description |
| ----------- | ----------- | 
| conductor-workflow-event-listener | Event Listeners for Conductor  |

## Backward Compatibility
Workflow event listeners are part of `conductor-contribs` binary as well - if you are already consuming contribs module as part of your build,
you do not need to add this as a separate dependency.
Core conductor-server also includes event listeners via contribs dependency.

## Configuration

### Workflow Archival
Set the following properties to archive the workflows as they complete.  
When archived, the workflow execution is removed from the primary DAO and pushed to index store (e.g. Elasticsearch)
```properties
conductor.workflow-status-listener.type=archive

#when non-zero, workflows are removed from the primary storage after the TTL expiry
conductor.workflow-status-listener.archival.ttlDuration=0

#number of threads for the background worker that processes the archival request
conductor.workflow-status-listener.archival.delayQueueWorkerThreadCount=5
```

### Queue publisher
Publish a summary of workflow [WorkflowSummary](https://github.com/conductor-oss/conductor/blob/main/common/src/main/java/com/netflix/conductor/common/run/WorkflowSummary.java) 
to a queue as the workflow gets completed.

```properties
conductor.workflow-status-listener.type=queue_publisher

#Queue for successful completion of a workflow
conductor.workflow-status-listener.queue-publisher.successQueue=_callbackSuccessQueue

#Queue for failed workflows
conductor.workflow-status-listener.queue-publisher.failureQueue=_callbackFailureQueue

#Queue for terminal state workflows (success or failed)
conductor.workflow-status-listener.queue-publisher.finalizeQueue=_callbackFinalizeQueue
```
