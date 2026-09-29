# GuardrailCompiler routes a rejected decision to a TERMINATE task whose reason
# references that decision's message. Require that causal link, not just a
# rejection somewhere in an otherwise broken workflow.
. as $workflow
| .status == "FAILED"
  and any(.tasks[];
    . as $guard
    | (.outputData.result // .outputData) as $decision
    | .status == "COMPLETED"
      and ($decision | type) == "object"
      and $decision.passed == false
      and $decision.on_fail == "raise"
      and ($decision.guardrail_name | type) == "string"
      and ($decision.guardrail_name | length) > 0
      and ($decision.message | type) == "string"
      and $workflow.reasonForIncompletion == $decision.message
      and any($workflow.tasks[];
        .taskType == "TERMINATE"
        and .status == "COMPLETED"
        and .inputData.terminationStatus == "FAILED"
        and .inputData.terminationReason == $decision.message
        and (.workflowTask.inputParameters.terminationReason as $reason
          | ($guard.workflowTask.taskReferenceName // $guard.referenceTaskName) as $ref
          | $reason == ("${" + $ref + ".output.result.message}")
            or $reason == ("${" + $ref + ".output.message}"))))
  and all(.tasks[] | select(.taskType == "LLM_CHAT_COMPLETE"); .status == "COMPLETED")
