# While
```json
"type" : "WHILE"
```

The `WHILE` task is similar to the `DO_WHILE` task in that it sequentially executes a list of tasks as long as a condition is true. However, unlike the `DO_WHILE` task, the `WHILE` task checks the condition before executing the task list, and if the condition is initially false, the task list will not be executed.

When scheduled, each task within the `WHILE` task will have its `taskReferenceName` concatenated with __i, where i is the iteration number starting at 1. It is important to note that `taskReferenceName` containing arithmetic operators must not be used.

Similar to the `DO_WHILE` task, the output of each task is stored as part of the `WHILE` task, indexed by the iteration value, allowing the condition to reference the output of a task for a specific iteration.

The `WHILE` task is set to `FAILED` as soon as one of the loopOver tasks fails. In such a case, the iteration is retried starting from 1.

## Limitations 
- Domain or isolation group execution is unsupported.
- Nested `WHILE` tasks are unsupported, however, the `WHILE` task supports using `SUB_WORKFLOW` as a loopOver task, allowing for similar functionality.
- Since loopOver tasks will be executed in a loop inside the scope of the parent `WHILE` task, branching outside of the `WHILE` task is not respected.

Branching inside loopOver task is supported.

## Configuration
The following fields must be specified at the top level of the task configuration.

| name          | type        | description                                                                                                                                                                                                          |
|---------------|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| loopCondition | String      | Condition to be evaluated after every iteration. This is a Javascript expression, evaluated using the Nashorn engine. If an exception occurs during evaluation, the WHILE task is set to FAILED_WITH_TERMINAL_ERROR. |
| loopOver      | List\[Task] | List of tasks that needs to be executed as long as the condition is true.                                                                                                                                            |

## Output

| name      | type             | description                                                                                                                                                                                           |
|-----------|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| iteration | Integer          | Iteration number: the current one while executing; the final one once the loop is finished                                                                                                            |
| `i`       | Map[String, Any] | Iteration number as a string, mapped to the task references names and their output.                                                                                                                   |
| *         | Any              | Any state can be stored here if the `loopCondition` does so. For example `storage` will exist if `loopCondition` is `if ($.LoopTask['iteration'] <= 10) {$.LoopTask.storage = 3; true } else {false}` |

## Examples
### Basic Example

The following definition:
```json
{
    "name": "Loop Task",
    "taskReferenceName": "LoopTask",
    "type": "WHILE",
    "inputParameters": {
      "value": "${workflow.input.value}"
    },
    "loopCondition": "if ( ($.LoopTask['iteration'] < $.value ) || ( $.first_task['response']['body'] > 10)) { false; } else { true; }",
    "loopOver": [
        {
            "name": "first task",
            "taskReferenceName": "first_task",
            "inputParameters": {
                "http_request": {
                    "uri": "http://localhost:8082",
                    "method": "POST"
                }
            },
            "type": "HTTP"
        },{
            "name": "second task",
            "taskReferenceName": "second_task",
            "inputParameters": {
                "http_request": {
                    "uri": "http://localhost:8082",
                    "method": "POST"
                }
            },
            "type": "HTTP"
        }
    ],
    "startDelay": 0,
    "optional": false
}
```

will produce the following execution, assuming 3 executions occurred (alongside `first_task__1`, `first_task__2`, `first_task__3`,
`second_task__1`, `second_task__2` and `second_task__3`):

```json
{
    "taskType": "WHILE",
    "outputData": {
        "iteration": 3,
        "1": {
            "first_task": {
                "response": {},
                "headers": {
                    "Content-Type": "application/json"
                }
            },
            "second_task": {
                "response": {},
                "headers": {
                    "Content-Type": "application/json"
                }
            }
        },
        "2": {
            "first_task": {
                "response": {},
                "headers": {
                    "Content-Type": "application/json"
                }
            },
            "second_task": {
                "response": {},
                "headers": {
                    "Content-Type": "application/json"
                }
            }
        },
        "3": {
            "first_task": {
                "response": {},
                "headers": {
                    "Content-Type": "application/json"
                }
            },
            "second_task": {
                "response": {},
                "headers": {
                    "Content-Type": "application/json"
                }
            }
        }
    }
}
```

### Example using iteration key

Sometimes, you may want to use the iteration value/counter in the tasks used in the loop.  In this example, an API call is made to GitHub (to the Netflix Conductor repository), but each loop increases the pagination.

The Loop ```taskReferenceName``` is "get_all_stars_loop_ref".

In the ```loopCondition``` the term ```$.get_all_stars_loop_ref['iteration']``` is used.

In tasks embedded in the loop, ```${get_all_stars_loop_ref.output.iteration}``` is used.  In this case, it is used to define which page of results the API should return.

```json
{
    "name": "get_all_stars",
    "taskReferenceName": "get_all_stars_loop_ref",
    "inputParameters": {
        "stargazers": [
            1,
            2
        ]
    },
    "type": "WHILE",
    "startDelay": 0,
    "optional": false,
    "asyncComplete": false,
    "loopCondition": "if ( $.stargazers && $.get_all_stars_loop_ref['iteration'] < $.stargazers.length ) { true; } else { false; }",
    "loopOver": [
        {
            "name": "100_stargazers",
            "taskReferenceName": "hundred_stargazers_ref",
            "inputParameters": {
                "counter": "${get_all_stars_loop_ref.output.iteration}",
                "http_request": {
                    "uri": "https://api.github.com/repos/netflix/conductor/stargazers?page=${get_all_stars_loop_ref.output.iteration}&per_page=100",
                    "method": "GET",
                    "headers": {
                        "Authorization": "token ${workflow.input.gh_token}",
                        "Accept": "application/vnd.github.v3.star+json"
                    }
                }
            },
            "type": "HTTP",
            "startDelay": 0,
            "optional": false,
            "asyncComplete": false,
            "retryCount": 3
        }
    ]
}
```
