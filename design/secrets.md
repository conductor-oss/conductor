# Support for handling secrets in Tasks
Workers often need sensitive information to operate on.  e.g. api keys, to make calls, run LLM tasks etc.
This is a proposal to pass such information back to the worker without having to inject it as part of the workflow definition

# High Level Approach
Task definition declares all the secrets and env variables it needs from the server.  This should be persent in the TaskDef.  At runtime, server checks the task definition and injects the necessary values as K,V in the Task.

# Changes needed
### TaskDef.java
Add a new parameter that lists secrets / env variables

### Task.java
Add a new paramter (K, V) with key being the name of the secret / env variable

### Task Polling
When a task is polled, checck the required secrets / env variables based on the task def.  Find them from 1) secrets store or 2) env variables in that order, wire them up in Task and send back to the caller
