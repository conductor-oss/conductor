# Set Variable

```json
"type" : "SET_CORRELATION_ID"
```

The `SET_CORRELATION_ID` task allows users to set the correlation ID on a workflow from within the workflow. Normally the correlation ID would be set by the process starting the workflow, however in certain occasions, such as starting a workflow from an event subscription, this is not possible.

## Use Cases

If you're using event handlers to start workflows and you're able to pass in the correlation ID in the event data, then this task enables you to set the current workflow to have that correlation ID too, making it easier to correlate workflows across system boundaries.

## Configuration

Set the new correlation ID in the `correlationId` input variable.

## Example

In this example, there is an S3 bucket that receives an XML file (which includes the correlation ID), this triggers an event notification in SQS for a new file which triggers this workflow. There is a SIMPLE task which parses the XML and outputs JSON including the correlation ID in the data structure. This task output is then used to set the correlatin ID on this workflow.

```json
{
  "name": "Set_Correlation_Id_Workflow",
  "description": "Set the correlation id to a value",
  "version": 1,
  "tasks": [
    {
      "name": "convert_xml",
      "taskReferenceName": "convert_xml",
      "inputParameters": {
        "s3Path": "${workflow.input.s3Path}"
      },
      "type": "SIMPLE"
    },
    {
      "name": "set_correlation_id",
      "taskReferenceName": "set_correlation_id",
      "type": "SET_CORRELATION_ID",
      "inputParameters": {
        "correlationId": "${convert_xml.output.correlationId}"
      }
    },
    {
      "name": "process_data",
      "taskReferenceName": "process_data",
      "inputParameters": {
        "saved_name": "${convert_xml.output.data}"
      },
      "type": "SIMPLE"
    }
  ],
  "restartable": true,
  "ownerEmail": "abc@example.com",
  "workflowStatusListenerEnabled": true,
  "schemaVersion": 2
}
```

In the above example, it can be seen that the task `Set_Name` is a Set Variable Task and
the variable `name` is set to `Foo` and later in the workflow it is referenced by
`"${workflow.variables.name}"` in another task.
