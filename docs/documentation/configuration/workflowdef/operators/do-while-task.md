# Do While
```json
"type" : "DO_WHILE"
```

The Do While task (`DO_WHILE`) sequentially executes a list of tasks as long as a given condition is true. The sequence of tasks gets executed before the condition is checked, even for the first iteration, just like a regular _do.. while_ statement in programming.

## Task parameters

Use these parameters in top level of the Do While task configuration.

| Parameter          | Type                | Description                                       | Required / Optional  |
| ------------------ | ------------------- | ------------------------------------------------- | -------------------- |
| loopCondition | String      | The condition that is evaluated after each iteration. This is a JavaScript expression, evaluated using the Nashorn engine. | Required. |
| loopOver      | List[Task] | The list of task configurations that will be executed as long as the condition is true.                                                                                                                                               | Required. |

## Input parameters

Use these parameters in the `inputParameters` section of the Do While task configuration.

| Parameter     | Type    | Description                                                                                                                                                                                                                                                                                        | Required / Optional |
| ------------- | ------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------- |
| keepLastN     | Integer | Number of most recent iterations to keep in the database and task output. Older iterations are automatically removed to prevent database bloat. When not specified, all iterations are retained (default behavior). This is useful for long-running loops with many iterations. Minimum value: 1. | Optional.           |

## JSON configuration

Here is the task configuration for a Do While task.

```json
{
  "name": "do_while",
  "taskReferenceName": "do_while_ref",
  "inputParameters": {
    "keepLastN": 10
  },
  "type": "DO_WHILE",
  "loopCondition": "(function () {\n  if ($.do_while_ref['iteration'] < 5) {\n    return true;\n  }\n  return false;\n})();",
  "loopOver": [ // List of tasks to be executed in the loop
    {
        // task configuration
    },
    {
        // task configuration
    }
  ]
}
```

## Output

The Do While task will return the following parameters.

| Name             | Type         | Description                                                   |
| ---------------- | ------------ | ------------------------------------------------------------- |
| iteration | Integer          | The number of iterations. <br/><br/> If the Do While task is in progress, `iteration` will show the current iteration number. When completed, `iteration` will show the final number of iterations.                          |

In addition, a map will be created for each iteration, keyed by its iteration number (e.g., 1, 2, 3), and will contain the task outputs for all of the `loopOver` tasks.

Furthermore, if `loopCondition` declares any parameter, it will also appear in the output. For example, `storage` will appear in the output if `loopCondition` is `if ($.LoopTask['iteration'] <= 10) {$.LoopTask.storage = 3; true } else {false}`.

## Execution

When a Do While loop is executed, each task in the loop will have its `taskReferenceName` concatenated with _\_\_i_, with _i_ as the iteration number starting at 1. If one of the loop tasks fails, the Do While task status will be set as FAILED, and upon retry, the iteration number will restart from 1.

Each loop task output is stored as part of the Do While task, indexed by the iteration value, allowing `loopCondition` to reference the output of a task for a specific iteration (e.g., `$.LoopTask['iteration]['first_task']`).


## Iteration cleanup

For Do While loops with many iterations (e.g., 100+ iterations), storing all iteration data can lead to database bloat, memory exhaustion, and performance degradation. The `keepLastN` input parameter provides automatic cleanup of old iterations.

**How it works:**

When `keepLastN` is specified in `inputParameters`, Conductor automatically removes old iteration data from both the database and the task output once the number of iterations exceeds the `keepLastN` value. For example, with `keepLastN: 5`:

- Iterations 1-5: All iterations kept
- Iteration 6: Iteration 1 is removed, keeping iterations 2-6
- Iteration 7: Iteration 2 is removed, keeping iterations 3-7
- And so on...

**Important considerations:**

- **Opt-in behavior:** Cleanup only occurs when `keepLastN` is explicitly set. Without this parameter, all iterations are retained (default behavior).
- **Backward compatibility:** Existing workflows without `keepLastN` continue to work unchanged.
- **Output data:** Only the most recent N iterations will be available in the task output. Older iterations are permanently removed.
- **Loop condition:** Ensure your `loopCondition` only references recent iterations if using `keepLastN`, as older iteration data will not be available.
- **Best practices:**
  - For loops expected to run 100+ iterations, consider setting `keepLastN` to a reasonable value (e.g., 5-10).
  - Choose a `keepLastN` value that balances memory usage with your need to access historical iteration data.
  - If your `loopCondition` needs to reference older iterations, ensure `keepLastN` is set high enough to retain that data.

**Example with cleanup:**

```json
{
  "name": "long_running_loop",
  "taskReferenceName": "long_running_loop_ref",
  "inputParameters": {
    "keepLastN": 5
  },
  "type": "DO_WHILE",
  "loopCondition": "if ($.long_running_loop_ref['iteration'] < 1000) { true; } else { false; }",
  "loopOver": [
    {
      "name": "process_item",
      "taskReferenceName": "process_item_ref",
      "type": "SIMPLE"
    }
  ]
}
```

In this example, even though the loop runs 1000 iterations, only the last 5 iterations are kept in the database and output at any given time, preventing database bloat.

## Examples

Here are some examples for using the Do While task.

### Using a basic script

In this example task configuration, the Do While task evaluates two criteria:


```json
{
    "name": "Loop",
    "taskReferenceName": "LoopTask",
    "type": "DO_WHILE",
    "inputParameters": {
      "value": "${workflow.input.value}"
    },
    "loopCondition": "if ( ($.LoopTask['iteration'] < $.value ) || ( $.first_task['response']['body'] > 10)) { false; } else { true; }",
    "loopOver": [
        {
            "name": "firstTask",
            "taskReferenceName": "first_task",
            "inputParameters": {
                "http_request": {
                    "uri": "http://localhost:8082",
                    "method": "POST"
                }
            },
            "type": "HTTP"
        },{
            "name": "secondTask",
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

Assuming three executions occurred (`first_task__1`, `first_task__2`, `first_task__3`,
`second_task__1`, `second_task__2`, and `second_task__3`), the Do While task will return the following will produce the following output: 

```json
{
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
```

### Using the iteration key in a loop task

Sometimes, you may want to use the Do While iteration value/counter inside your loop tasks. In this example, an API call is made to a GitHub repository to get all stargazers and each iteration increases the pagination.

To evaluate the current iteration, the parameter `$.get_all_stars_loop_ref['iteration']` is used in `loopCondition`. In the HTTP task embedded in the loop, `${get_all_stars_loop_ref.output.iteration}` is used to define which page the API should return.


```json
{
    "name": "get_all_stars",
    "taskReferenceName": "get_all_stars_loop_ref",
    "inputParameters": {
        "stargazers": "4000"
    },
    "type": "DO_WHILE",
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
            "type": "HTTP"
        }
    ]
}
```


## Limitations

There are several limitations for the Do While task:

- **Branching**—Within a Do While task, branching using Switch, Fork/Join, Dynamic Fork tasks are supported. However, since the loop tasks will be executed within the scope of the Do While task, any branching that crosses outside its scope will not be respected.
- **Nested loops**—Nested Do While tasks are not supported. To achieve a similar functionality as a nested loop, you can use a [Sub Workflow](sub-workflow-task.md) task inside the Do While task.
- **Isolation group execution**—Isolation group execution is not supported. However, domain is supported for loop tasks inside the Do While task.
