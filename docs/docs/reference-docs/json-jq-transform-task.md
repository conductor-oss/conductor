---
sidebar_position: 1
---

# JSON JQ Transform Task

```json
"type" : "JSON_JQ_TRANSFORM_TASK"
```
### Introduction

JSON_JQ_TRANSFORM_TASK is a System task that allows processing of JSON data that is supplied to the task, by using the
popular JQ processing tool’s query expression language.

Check the [JQ Manual](https://stedolan.github.io/jq/manual/v1.5/), and the
[JQ Playground](https://jqplay.org/) for more information on JQ

### Use Cases

JSON is a popular format of choice for data-interchange. It is widely used in web and server applications, document
storage, API I/O etc. It’s also used within Conductor to define workflow and task definitions and passing data and state
between tasks and workflows. This makes a tool like JQ a natural fit for processing task related data. Some common
usages within Conductor includes, working with HTTP task, JOIN tasks or standalone tasks that try to transform data from
the output of one task to the input of another.

### Configuration


| Attribute      | Description |
| ----------- | ----------- |
| name      | Task Name. A unique name that is descriptive of the task function      |
| taskReferenceName   | Task Reference Name. A unique reference to this task. There can be multiple references of a task within the same workflow definition        |
| type   | Task Type. In this case, JSON_JQ_TRANSFORM        |
| inputParameters   | The input parameters that will be supplied to this task. The parameters will be a JSON object of atleast 2 attributes, one of which will be called queryExpression. The others are user named attributes. These attributes will be accessible by the JQ query processor        |
| inputParameters/user-defined-key(s)   | User defined key(s) along with values.          |
| inputParameters/queryExpression   | A JQ query expression        |

#### Output Configuration

| Attribute      | Description |
| ----------- | ----------- |
| result   | The first results returned by the JQ expression     |
| resultList   | A List of results returned by the JQ expression        |
| error | An optional error message, indicating that the JQ query failed processing |

### Example

Here is an example of a _`JSON_JQ_TRANSFORM`_ task. The `inputParameters` attribute is expected to have a value object
that has the following

1. A list of key value pair objects denoted key1/value1, key2/value2 in the example below. Note the key1/value1 are
   arbitrary names used in this example.

2. A key with the name `queryExpression`, whose value is a JQ expression. The expression will operate on the value of
   the `inputParameters` attribute. In the example below, the `inputParameters` has 2 inner objects named by attributes
   `key1` and `key2`, each of which has an object that is named `value1` and `value2`. They have an associated array of
   strings as values, `"a", "b"` and `"c", "d"`. The expression `key3: (.key1.value1 + .key2.value2)` concat's the 2
   string arrays into a single array against an attribute named `key3`

```json
{
  "name": "jq_example_task",
  "taskReferenceName": "my_jq_example_task",
  "type": "JSON_JQ_TRANSFORM",
  "inputParameters": {
    "key1": {
      "value1": [
        "a",
        "b"
      ]
    },
    "key2": {
      "value2": [
        "c",
        "d"
      ]
    },
    "queryExpression": "{ key3: (.key1.value1 + .key2.value2) }"
  }
}
```

The execution of this example task above will provide the following output. The `resultList` attribute stores the full
list of the `queryExpression` result. The `result` attribute stores the first element of the resultList. An
optional `error`
attribute along with a string message will be returned if there was an error processing the query expression.

```json
{
  "result": {
    "key3": [
      "a",
      "b",
      "c",
      "d"
    ]
  },
  "resultList": [
    {
      "key3": [
        "a",
        "b",
        "c",
        "d"
      ]
    }
  ]
}
```

#### Input Configuration