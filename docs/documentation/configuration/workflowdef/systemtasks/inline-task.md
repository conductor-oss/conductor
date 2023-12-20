# Inline Task

```json
"type": "INLINE"
```

The `INLINE` task helps execute necessary logic at workflow runtime,
using an evaluator. There are two supported evaluators as of now:

## Configuration
The `INLINE` task is configured by specifying the following keys inside `inputParameters`, along side any other input values required for the evaluation.

### inputParameters
| Name          | Type   | Description                                                                                                                                                                         | Notes              |
| ------------- | ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------ |
| evaluatorType | String | Type of the evaluator. Supported evaluators: `value-param`, `javascript` which evaluates javascript expression.                                                                     | Must be non-empty. |
| expression    | String | Expression associated with the type of evaluator. For `javascript` evaluator, Javascript evaluation engine is used to evaluate expression defined as a string. Must return a value. | Must be non-empty. |

Besides `expression`, any value is accessible as `$.value` for the `expression` to evaluate.

## Outputs

| Name   | Type | Description                                                             |
| ------ | ---- | ----------------------------------------------------------------------- |
| result | Map  | Contains the output returned by the evaluator based on the `expression` |

## Examples
### Example 1
``` json
{
  "name": "INLINE_TASK",
  "taskReferenceName": "inline_test",
  "type": "INLINE",
  "inputParameters": {
      "inlineValue": "${workflow.input.inlineValue}",
      "evaluatorType": "javascript",
      "expression": "function scriptFun(){if ($.inlineValue == 1){ return {testvalue: true} } else { return
      {testvalue: false} }} scriptFun();"
  }
}
```

The task output can then be referenced in downstream tasks using an expression:
`"${inline_test.output.result.testvalue}"`

!!!tip "Note"
    The JavaScript evaluator accepts JS code written to the ECMAScript 5.1(ES5) standard


### Example 2 

Perhaps a weather API sometimes returns Celcius, and sometimes returns Farenheit temperature values.  This task ensures that the downstream tasks ONLY receive Celcius values:

```
{
  "name": "INLINE_TASK",
  "taskReferenceName": "inline_test",
  "type": "INLINE",
  "inputParameters": {
      "scale": "${workflow.input.tempScale}",
	    "temperature": "${workflow.input.temperature}",
      "evaluatorType": "javascript",
      "expression": "function SIvaluesOnly(){if ($.scale === "F"){ centigrade = ($.temperature -32)*5/9; return {temperature: centigrade} } else { return 
      {temperature: $.temperature} }} SIvaluesOnly();"
  }
}
```
