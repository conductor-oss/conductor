### Note
This module provides a partial implementation of the ExecutionDAO using Cassandra as the datastore.  
The execution data is stored in Cassandra in the `workflows` table. A task to workflow mapping is also maintained in a separate `task_lookup` table.

All datastore operations that are used during the critical execution path of a workflow are currently implemented. This includes CRUD operations for workflows and tasks.

This module provides complete implementations for MetadataDAO and EventHandlerDAO interfaces.

This module does not provide implementations for the QueueDAO and PollDataDAO interfaces.
 