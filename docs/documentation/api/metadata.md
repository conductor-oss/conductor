# Metadata API
 
## Workflow Metadata
| Endpoint                                 | Description                      | Input                                                          |
|------------------------------------------|----------------------------------|----------------------------------------------------------------|
| `GET {{ api_prefix }}/metadata/workflow`                 | Get all the workflow definitions | n/a                                                            |
| `POST {{ api_prefix }}/metadata/workflow`                | Register new workflow            | [Workflow Definition](../configuration/workflowdef/index.md)         |
| `PUT {{ api_prefix }}/metadata/workflow`                 | Register/Update new workflows    | List of [Workflow Definition](../configuration/workflowdef/index.md) |
| `GET {{ api_prefix }}/metadata/workflow/{name}?version=` | Get the workflow definitions     | workflow name, version (optional)                              |

## Task Metadata
| Endpoint                                 | Description                      | Input                                                          |
|------------------------------------------|----------------------------------|----------------------------------------------------------------|
| `GET {{ api_prefix }}/metadata/taskdefs`                 | Get all the task definitions     | n/a                                                            |
| `GET {{ api_prefix }}/metadata/taskdefs/{taskType}`      | Retrieve task definition         | Task Name                                                      |
| `POST {{ api_prefix }}/metadata/taskdefs`                | Register new task definitions    | List of [Task Definitions](../configuration/taskdef.md)        |
| `PUT {{ api_prefix }}/metadata/taskdefs`                 | Update a task definition         | A [Task Definition](../configuration/taskdef.md)               |
| `DELETE {{ api_prefix }}/metadata/taskdefs/{taskType}`   | Delete a task definition         | Task Name                                                      |
