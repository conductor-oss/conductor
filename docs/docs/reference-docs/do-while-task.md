---
sidebar_position: 1
---

# Do-While
```json
"type" : "DO_WHILE"
```
## Introduction
Sequentially execute a list of task as long as a condition is true. 
The list of tasks is executed first, before the condition is checked (even for the first iteration).

When scheduled, each task of this loop will see its `taskReferenceName` concatenated with __i, with i being the iteration number, starting at 1. Warning: taskReferenceName containing arithmetic operators must not be used.

Each task output is stored as part of the DO_WHILE task, indexed by the iteration value (see example below), allowing the condition to reference the output of a task for a specific iteration (eg. $.LoopTask['iteration]['first_task'])

The DO_WHILE task is set to `FAILED` as soon as one of the loopOver fails. In such case retry, iteration starts from 1.

### Limitations 
- Domain or isolation group execution is unsupported; 
- Nested DO_WHILE is unsupported, however, DO_WHILE task supports SUB_WORKFLOW as loopOver task, so we can achieve similar functionality.
- Since loopover tasks will be executed in loop inside scope of parent do while task, crossing branching outside of DO_WHILE task is not respected.


Branching inside loopOver task is supported.



## Configuration

### Input Parameters:

| name          | type       | description                                                                                                                                                                                                             |
|---------------|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| loopCondition | String     | Condition to be evaluated after every iteration. This is a Javascript expression, evaluated using the Nashorn engine. If an exception occurs during evaluation, the DO_WHILE task is set to FAILED_WITH_TERMINAL_ERROR. |
| loopOver      | List[Task] | List of tasks that needs to be executed as long as the condition is true.                                                                                                                                               |

### Output Parameters

| name      | type             | description                                                                                                                                                                                           |
|-----------|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| iteration | Integer          | Iteration number: the current one while executing; the final one once the loop is finished                                                                                                            |
| `i`       | Map[String, Any] | Iteration number as a string, mapped to the task references names and their output.                                                                                                                   |
| *         | Any              | Any state can be stored here if the `loopCondition` does so. For example `storage` will exist if `loopCondition` is `if ($.LoopTask['iteration'] <= 10) {$.LoopTask.storage = 3; true } else {false}` |

## Examples

The following definition:
```json
{
    "name": "Loop Task",
    "taskReferenceName": "LoopTask",
    "type": "DO_WHILE",
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
    "taskType": "DO_WHILE",
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

## Example using iteration key

Sometimes, you may want to use the iteration value/counter in the tasks used in the loop.  In this example, an API call is made to GitHub (to the Netflix Conductor repository), but each loop increases the pagination.

The Loop ```taskReferenceName``` is "get_all_stars_loop_ref".

In the ```loopCondition``` the term ```$.get_all_stars_loop_ref['iteration']``` is used.

In tasks embedded in the loop, ```${get_all_stars_loop_ref.output.iteration}``` is used.  In this case, it is used to define which page of results the API should return.

```json
{
      "name": "get_all_stars",
      "taskReferenceName": "get_all_stars_loop_ref",
      "inputParameters": {
        "stargazers": "4000"
      },
      "type": "DO_WHILE",
      "decisionCases": {},
      "defaultCase": [],
      "forkTasks": [],
      "startDelay": 0,
      "joinOn": [],
      "optional": false,
      "defaultExclusiveJoinTask": [],
      "asyncComplete": false,
      "loopCondition": "if ($.get_all_stars_loop_ref['iteration'] < Math.ceil($.stargazers/100)) { true; } else { false; }",
      "loopOver": [
        {
          "name": "100_stargazers",
          "taskReferenceName": "hundred_stargazers_ref",
          "inputParameters": {
            "counter": "${get_all_stars_loop_ref.output.iteration}",
            "http_request": {
              "uri": "https://api.github.com/repos/ntflix/conductor/stargazers?page=${get_all_stars_loop_ref.output.iteration}&per_page=100",
              "method": "GET",
              "headers": {
                "Authorization": "token ${workflow.input.gh_token}",
                "Accept": "application/vnd.github.v3.star+json"
              }
            }
          },
          "type": "HTTP",
          "decisionCases": {},
          "defaultCase": [],
          "forkTasks": [],
          "startDelay": 0,
          "joinOn": [],
          "optional": false,
          "defaultExclusiveJoinTask": [],
          "asyncComplete": false,
          "loopOver": [],
          "retryCount": 3
        }
      ]
    }

```
