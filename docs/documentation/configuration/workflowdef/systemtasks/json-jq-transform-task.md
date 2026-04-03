# JSON JQ Transform Task
```json
"type" : "JSON_JQ_TRANSFORM"
```

The JSON JQ Transform task (`JSON_JQ_TRANSFORM`) processes JSON data using jq. It is useful for transforming data from one task's output into the input of another task.

## Task parameters

Use these parameters inside `inputParameters` in the JSON JQ Transform task configuration.


`queryExpression` is appended to the `inputParameters` of `JSON_JQ_TRANSFORM`, along side any other input values needed for the evaluation.

| Parameter          | Type                | Description                                       | Required / Optional  |
| ------------------ | ------------------- | ------------------------------------------------- | -------------------- |
| inputParameters.queryExpression | String | The jq filter, which is the expression used to transform the JSON data. <br/><br/> Refer to the [JQ Manual](https://stedolan.github.io/jq/manual/v1.5/) for more information on constructing filters. | Required. |
| inputParameters | Map[String, Any] | Contains the inputs for the jq transformation. | Required. |

## JSON configuration


Here is the task configuration for a JSON JQ Transform task.

```json
{
  "name": "json_transform",
  "taskReferenceName": "json_transform_ref",
  "type": "JSON_JQ_TRANSFORM",
  "inputParameters": {
    "persons": [
      {
        "name": "some",
        "last": "name",
        "email": "mail@mail.com",
        "id": 1
      },
      {
        "name": "some2",
        "last": "name2",
        "email": "mail2@mail.com",
        "id": 2
      }
    ],
    "queryExpression": ".persons | map({user:{email,id}})"
  }
}
```

## Output

The JSON JQ Transform task will return the following parameters.

| Name             | Type         | Description                                                   |
| ---------------- | ------------ | ------------------------------------------------------------- |
| result     | List[Map[String, Any]] | The first element of the `resultList` returned by the jq filter.                           |
| resultList | List[List[Map[String, Any]]] | A list of results returned by the jq filter.                           |
| error      | String | An optional error message if the jq filter failed. |



## Examples

Here are some examples for using the JSON JQ Transform task.

### Simple example

In this example, the jq filter expression `key3: (.key1.value1 + .key2.value2)` will concatenate the two provided string arrays in `key1` and `key2` into a single array named `key3`.

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

The above JSON JQ Transform task will provide the following output. In this case, both `resultList` and `result` are the same.

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

### Simplifying data

In this example, the JSON JQ Transform task is used to simplify and extract data from an extremely dense API response. The HTTP task retrieves a list of stargazers (users who have starred a repository) from GitHub, and the response for just one user looks like this:

``` json 
  
"body":[
  {
  "starred_at":"2016-12-14T19:55:46Z",
  "user":{
    "login":"lzehrung",
    "id":924226,
    "node_id":"MDQ6VXNlcjkyNDIyNg==",
    "avatar_url":"https://avatars.githubusercontent.com/u/924226?v=4",
    "gravatar_id":"",
    "url":"https://api.github.com/users/lzehrung",
    "html_url":"https://github.com/lzehrung",
    "followers_url":"https://api.github.com/users/lzehrung/followers",
    "following_url":"https://api.github.com/users/lzehrung/following{/other_user}",
    "gists_url":"https://api.github.com/users/lzehrung/gists{/gist_id}",
    "starred_url":"https://api.github.com/users/lzehrung/starred{/owner}{/repo}",
    "subscriptions_url":"https://api.github.com/users/lzehrung/subscriptions",
    "organizations_url":"https://api.github.com/users/lzehrung/orgs",
    "repos_url":"https://api.github.com/users/lzehrung/repos",
    "events_url":"https://api.github.com/users/lzehrung/events{/privacy}",
    "received_events_url":"https://api.github.com/users/lzehrung/received_events",
    "type":"User",
    "site_admin":false
  }
}
]
```

Since the only data required are the `starred_at` and `login` parameters for users who starred the repository after a given date (provided as a workflow input `${workflow.input.cutoff_date}`), we can use the JSON JQ Transform task to simplify the output:

```json
{
  "name": "jq_cleanup_stars",
  "taskReferenceName": "jq_cleanup_stars_ref",
  "inputParameters": {
    "starlist": "${hundred_stargazers_ref.output.response.body}",
    "queryExpression": "[.starlist[] | select (.starred_at > \"${workflow.input.cutoff_date}\") |{occurred_at:.starred_at, member: {github:  .user.login}}]"
  },
  "type": "JSON_JQ_TRANSFORM",
  "decisionCases": {},
  "defaultCase": [],
  "forkTasks": [],
  "startDelay": 0,
  "joinOn": [],
  "optional": false,
  "defaultExclusiveJoinTask": [],
  "asyncComplete": false,
  "loopOver": []
}
```

In the above task configuration, the API response JSON is stored in the `starlist` parameter.  The `queryExpression` reads the JSON, selects only entries where the `starred_at` value meets the date criteria, and generates output JSON in the following format:

```json
{
  "occurred_at": "date from JSON",
  "member":{
    "github" : "github Login from JSON"
  }
}
```

The `queryExpression` is wrapped in `[]` to indicate that the response should be an array.