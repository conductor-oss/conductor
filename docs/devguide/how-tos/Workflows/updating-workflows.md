# Updating Workflows

Workflows can be created or updated using the workflow metadata API

```html
PUT {{ api_prefix }}/metadata/workflow
```

### Example using curl 

```shell
curl '{{ server_host }}{{ api_prefix }}/metadata/workflow' \
  -X 'PUT' \
  -H 'accept: */*' \
  -H 'content-type: application/json' \
  --data-raw '[{"name":"sample_workflow","version":1,"tasks":[{"name":"ship_via_fedex","taskReferenceName":"ship_via_fedex","type":"SIMPLE"}],"schemaVersion":2}]'
```

### Example using node fetch

```javascript
fetch("{{ server_host }}{{ api_prefix }}/metadata/workflow", {
  "headers": {
    "accept": "*/*",
    "content-type": "application/json"
  },
  "body": "[{\"name\":\"sample_workflow\",\"version\":1,\"tasks\":[{\"name\":\"ship_via_fedex\",\"taskReferenceName\":\"ship_via_fedex\",\"type\":\"SIMPLE\"}],\"schemaVersion\":2}]",
  "method": "PUT"
});
```
## Best Practices

1. If you are updating the workflow with new tasks, remember to register the task definitions first
2. You can also use the Conductor Swagger UI to update the workflows 

