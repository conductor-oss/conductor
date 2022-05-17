# Archival Of Workflows

Conductor has support for archiving workflow upon termination or completion. Enabling this will delete the workflow from the configured database, but leave the associated data in Elasticsearch so it is still searchable. 

To enable, set the `conductor.workflow-status-listener.type` property to `archive`.

A number of additional properties are available to control archival.

| Property | Default Value | Description |
| -- | -- | -- |
| conductor.workflow-status-listener.archival.ttlDuration	| 0s | The time to live in seconds for workflow archiving module. Currently, only RedisExecutionDAO supports this |
| conductor.workflow-status-listener.archival.delayQueueWorkerThreadCount	| 5 | The number of threads to process the delay queue in workflow archival |
| conductor.workflow-status-listener.archival.delaySeconds |	60 | The time to delay the archival of workflow |
