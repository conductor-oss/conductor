import _pick from "lodash/pick";
import { InlineTaskInputParameters } from "types/TaskType";
import { WorkflowDef, WorkflowMetadataI } from "types/WorkflowDef";
import _keys from "lodash/keys";
import _difference from "lodash/difference";
import { TaskDef, TaskType } from "types/common";

export const extractWorkflowMetadata = (workflow: Partial<WorkflowDef>) =>
  _pick(workflow, [
    "name",
    "description",
    "version",
    "restartable",
    "workflowStatusListenerEnabled",
    "timeoutSeconds",
    "timeoutPolicy",
    "failureWorkflow",
    "ownerEmail",
    "updateTime",
    "inputParameters",
    "outputParameters",
    "inputSchema",
    "outputSchema",
    "enforceSchema",
    "tasks",
    "workflowStatusListenerSink",
    "rateLimitConfig",
    "metadata",
  ]) as Partial<WorkflowMetadataI>;

export const undeclaredInputParameters = (
  inputString: string,
  taskInputParams?: InlineTaskInputParameters | Record<string, unknown>,
) => {
  const matchedVariables = inputString.match(/(?<=\$\.)\w+/g);
  const inputParameters = _keys(taskInputParams);
  let addedInputParameters: string[] = [];
  if (matchedVariables) {
    addedInputParameters = _difference(matchedVariables, inputParameters);
  }
  return addedInputParameters;
};

export const invalidDollarVariables = (inputString: string) => {
  const regex = /\$(?:\.\$)*\.[\w$]+(?=[\s;\n])/g;
  const matches = inputString.match(regex);
  const filteredMatches = matches?.filter(
    (match: any) => (match.match(/\$/g) || []).length > 1,
  );
  return filteredMatches ?? [];
};

export const extractVariablesFromTask = (tasksInCrumbBranch: TaskDef[]) => {
  const setVariableInputs = tasksInCrumbBranch.reduce((acc, task) => {
    if (task?.type === TaskType.SET_VARIABLE) {
      Object.assign(acc, task?.inputParameters || {});
    }
    return acc;
  }, {});
  return Object.keys(setVariableInputs);
};
