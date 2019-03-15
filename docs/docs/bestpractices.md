## Response Timeout
- Configure the responseTimeoutSeconds of each task to be > 0.
- Should be less than or equal to timeoutSeconds.

## Payload sizes
- Configure your workflows such that conductor is not used as a persistence store.
- Ensure that the output data in the task result set in your worker is used by your workflow for execution. If the values in the output payloads are not used by subsequent tasks in your workflow, this data should not be sent back to conductor in the task result.
- In cases where the output data of your task is used within subsequent tasks in your workflow but is substantially large (> 100KB), consider uploading this data to an object store (S3 or similar) and set the location to the object in your task output. The subsequent tasks can then download this data from the given location and use it during execution.
