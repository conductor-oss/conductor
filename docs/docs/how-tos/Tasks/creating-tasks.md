# Creating Task Definitions

Tasks can be created using the tasks metadata API

`POST /api/metadata/taskdefs`

This API takes an array of new task definitions.

### Example using curl

```shell
curl 'http://localhost:8080/api/metadata/taskdefs' \
  -H 'accept: */*' \
  -H 'content-type: application/json' \
  --data-raw '[{"createdBy":"user","name":"sample_task_name_1","description":"This is a sample task for demo","responseTimeoutSeconds":10,"timeoutSeconds":30,"inputKeys":[],"outputKeys":[],"timeoutPolicy":"TIME_OUT_WF","retryCount":3,"retryLogic":"FIXED","retryDelaySeconds":5,"inputTemplate":{},"rateLimitPerFrequency":0,"rateLimitFrequencyInSeconds":1}]'
```

### Example using node fetch

```javascript
fetch("http://localhost:8080/api/metadata/taskdefs", {
    "headers": {
        "accept": "*/*",
        "content-type": "application/json",
    },
    "body": "[{\"createdBy\":\"user\",\"name\":\"sample_task_name_1\",\"description\":\"This is a sample task for demo\",\"responseTimeoutSeconds\":10,\"timeoutSeconds\":30,\"inputKeys\":[],\"outputKeys\":[],\"timeoutPolicy\":\"TIME_OUT_WF\",\"retryCount\":3,\"retryLogic\":\"FIXED\",\"retryDelaySeconds\":5,\"inputTemplate\":{},\"rateLimitPerFrequency\":0,\"rateLimitFrequencyInSeconds\":1}]",
    "method": "POST"
});
```
## Best Practices

1. You can update a set of tasks together in this API
2. Task configurations are important attributes that controls the behavior of this task in a Workflow. Refer to [Task Configurations](/configuration/taskdef.html) for all the options and details' 
3. You can also use the Conductor Swagger UI to update the tasks

