# Switch
```json
"type" : "SWITCH"
```

A `SWITCH` task is similar to the `switch...case` statement in a programming language. Two [evaluators](#evaluator-types-and-expression) are supported. In either case, the `expression` is evaluated (this could be a simply a referefence to `inputParameters`, or a more complex Javascript expression), and the appropriate task is executed based on the output of the `expression`, and the `decisionCases` defined in the task configuration.

## Use Cases
Useful in any situation where we have to execute one of many task options.

## Configuration

The following parameters are specified at the top level of the task configuration.

| name          | type                     | description                                                                                                                                                      |
| ------------- | ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| evaluatorType | String (enum)            | Type of the evaluator used. Supported types: `value-param`, `javascript`.                                                                                        |
| expression    | String                   | Depends on the [evaluatorType](#evaluator-types-and-expression)                                                                                                  |
| decisionCases | Map[String, List\[task]] | Map where the keys are the possible values that can result from `expression` being evaluated by `evaluatorType` with values being lists of tasks to be executed. |
| defaultCase   | List\[task]              | List of tasks to be executed when no matching value if found in `decisionCases`                                                                                  |

Note: If the evaluation type is `value-param`, `inputParameters` must be populated with the key specified in `expression`. 

### Evaluator Types and Expression
| evaluatorType | expression                     | description                                       |
| ------------- | ------------------------------ | ------------------------------------------------- |
| value-param   | String (name of key)           | Reference to provided key in `inputParameters`    |
| javascript    | String (JavaScript expression) | Evaluate JavaScript expressions and compute value |


## Output

| name             | type         | description                                                   |
| ---------------- | ------------ | ------------------------------------------------------------- |
| evaluationResult | List[String] | A List of string representing the list of cases that matched. |


## Examples

In this example workflow, we have to ship a package with the shipping service providers on the basis of input provided
while running the workflow.

Let's create a Workflow with the following switch task definition that uses `value-param` evaluatorType:

```json
{
  "name": "switch_task",
  "taskReferenceName": "switch_task",
  "inputParameters": {
    "switchCaseValue": "${workflow.input.service}"
  },
  "type": "SWITCH",
  "evaluatorType": "value-param",
  "expression": "switchCaseValue",
  "defaultCase": [
    {
      ...
    }
  ],
  "decisionCases": {
    "fedex": [
      {
        ...
      }
    ],
    "ups": [
      {
        ...
      }
    ]
  }
}
```

In the definition above the value of the parameter `switch_case_value`
is used to determine the switch-case. The evaluator type is `value-param` and the expression is a direct reference to
the name of an input parameter. If the value of `switch_case_value` is `fedex` then the decision case `ship_via_fedex`is
executed as shown below.

![Conductor UI - Workflow Run](Switch_Fedex.png)

In a similar way - if the input was `ups`, then `ship_via_ups` will be executed. If none of the cases match then the
default option is executed.

Here is an example using the `javascript` evaluator type:

```json
{
  "name": "switch_task",
  "taskReferenceName": "switch_task",
  "inputParameters": {
    "inputValue": "${workflow.input.service}"
  },
  "type": "SWITCH",
  "evaluatorType": "javascript",
  "expression": "$.inputValue == 'fedex' ? 'fedex' : 'ups'",
  "defaultCase": [
    {
      ...
    }
  ],
  "decisionCases": {
    "fedex": [
      {
        ...
      }
    ],
    "ups": [
      {
        ...
      }
    ]
  }
}
```
