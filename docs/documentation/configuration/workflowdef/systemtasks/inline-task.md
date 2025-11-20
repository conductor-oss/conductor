# Inline Task
```json
"type": "INLINE"
```

The Inline task (`INLINE`) executes lightweight scripting logic inside the Conductor server JVM and immediately returns a result that can be wired into downstream tasks.

The Inline task is best for small, deterministic logic like simple validation or calculation. For heavy, custom logic, it is best to use a Worker task (`SIMPLE`) instead.

## Task parameters

Use these parameters inside `inputParameters` in the Inline task configuration.

| Parameter          | Type                | Description                                       | Required / Optional  |
| ------------------ | ------------------- | ------------------------------------------------- | -------------------- |
| inputParameters.evaluatorType | String | The type of evaluator used. Supported types: `javascript`                             | Required. |
| inputParameters.expression    | String | The expression to be evaluated by the evaluator. The expression must return a value. <br/><br/> The JavaScript evaluator accepts code written to the ECMAScript 5.1(ES5) standard. <br/><br/>  **Note:** To use ES6 instead, set the environment variable `CONDUCTOR_NASHORN_ES6_ENABLED` to `true`. | Required. |
| inputParameters    | Map[String, Any] | Any other input parameters for the Inline task. You can include any other input values required for evaluation here, which can be referenced in `expression` as `$.value`. | Optional. |

## JSON configuration

Here is the task configuration for an Inline task.

```json
{
  "name": "inline",
  "taskReferenceName": "inline_ref",
  "type": "INLINE",
  "inputParameters": {
    "evaluatorType": "javascript",
    "expression": "(function(){ return $.input1 + $.input2; })()",
    "input1": 1,
    "input2": 2
  }
}
```


## Output

The Inline task will return the following parameters.

| Name             | Type         | Description                                                   |
| ---------------- | ------------ | ------------------------------------------------------------- |
| result | Map  | Contains the output returned by the evaluator based on the `expression`. |

## Examples

Here are some examples for using the Inline task.

### Simple example

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

The Inline task output can then be referenced in downstream tasks using the expression
`"${inline_test.output.result.testvalue}"`.


### Formatting data

In this example, the Inline task is used to ensure that downstream tasks only receive weather data in Celcius.

``` json
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
