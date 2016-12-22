Each task in the blueprint takes a set of inputs defined using ```inputParameters ``` block.
Here is a sample workflow task in a blueprint:

``` json
   {
      "name": "name_of_task",
      "taskReferenceName": "ref_name_unique_within_blueprint",
      "inputParameters": {
        "movieId": "${workflow.input.movieId}",
        "url": "${workflow.input.fileLocation}"
      },
      "type": "SIMPLE"
    }
```
inputParameters is a map with keys representing the task input.  The value of the key identifies the source of data at runtime.  

The syntax is as follows:
```source.input|output.key```

The source can be either "workflow" or a task reference name.
e.g. ```encode_task.output.location``` will read the _location_ key from the output of the task identified by reference name _encode_task_.

String and numeric literals are wired directly as the JSON string or numeric literals.
e.g.

``` json
{
 "some_key": "const string value",
 "num_value": 42
}
```
