import _entries from "lodash/entries";
import _first from "lodash/first";
import _isArray from "lodash/isArray";
import _isEmpty from "lodash/isEmpty";
import _isObject from "lodash/isObject";
import _nth from "lodash/nth";
import { getEnvVariables } from "pages/definition/commonService";
import { fetchContextNonHook, fetchWithContext } from "plugins/fetch";
import { queryClient } from "queryClient";
import { InlineTaskDef, TaskDef, TaskType } from "types";
import { WorkflowDef } from "types/WorkflowDef";
import { DEFAULT_WF_ATTRIBUTES } from "utils/constants";
import { FEATURES, featureFlags } from "utils/flags";
import {
  getVariablesForEachTasks,
  validateExpressionWithInputParams,
} from "./helpers";
import {
  ErrorIds,
  ErrorInspectorMachineContext,
  ErrorSeverity,
  ErrorTypes,
  ReferenceProblems,
  RefractorObject,
  TaskErrors,
  TaskReferenceReportingParameters,
  TaskWithUnknownReference,
  ValidationError,
} from "./types";

const fetchContext = fetchContextNonHook();

const enabledAdvancedValidations = featureFlags.isEnabled(
  FEATURES.ADVANCED_ERROR_INSPECTOR_VALIDATIONS,
);

export const valueContainsTaskReference = (
  value: any,
  taskReferences: string[],
  path = "",
): string[] => {
  if (typeof value === "string") {
    const hasReference = taskReferences.some(
      (tr) =>
        value.includes(`{${tr}.`) ||
        value.includes(`{${tr}}`) ||
        value.includes(`{workflow.input}`) ||
        value.includes(`{workflow.secrets}`) ||
        value.includes(`{workflow.env}`),
    );
    // this regex will allow letters,digits,underscore,dot and hyphen
    const pattern =
      /\${(?!.*(\$|\{|}|<>|-)\1)[a-zA-Z_\d-[\]]+(\.[a-zA-Z_\d-[\]]+)*(\.[a-zA-Z_]+\(\))?}/;

    const expectedReferences = [
      ...taskReferences,
      "workflow.input",
      "workflow.secrets",
      "workflow.env",
    ];

    const isValidVariable = enabledAdvancedValidations
      ? pattern.test(value) || isValidNestedVariable(expectedReferences, value)
      : pattern.test(value);

    return value.includes("${") && (!hasReference || !isValidVariable)
      ? [path]
      : [];
  }
  if (_isArray(value)) {
    return value.flatMap((v) =>
      valueContainsTaskReference(v, taskReferences, path),
    );
  }

  if (_isObject(value)) {
    return Object.entries(value).flatMap(
      ([key, value]) =>
        valueContainsTaskReference(value, taskReferences, `${path}.${key}`),
      0,
    );
  }

  return [];
};

export const valueContainsVariableTaskReference = (
  value: any,
  taskReferences: string[],
  path = "",
): string[] => {
  if (typeof value === "string" && value.startsWith("${workflow.variables.")) {
    const hasReference = taskReferences.some(
      (tr) => value.includes(`{${tr}.`) || value.includes(`{${tr}}`),
    );
    const pattern =
      /\${(?!.*(\$|\{|}|<>|-)\1)[a-zA-Z_\d-[\]]+(\.[a-zA-Z_\d-[\]]+)*(\.[a-zA-Z_]+\(\))?}/;

    const isValidVariable = pattern.test(value);

    return value.includes("${") && isValidVariable && !hasReference
      ? [path]
      : [];
  }
  if (_isArray(value)) {
    return value.flatMap((v) =>
      valueContainsVariableTaskReference(v, taskReferences, path),
    );
  }

  if (_isObject(value)) {
    return Object.entries(value).flatMap(
      ([key, value]) =>
        valueContainsVariableTaskReference(
          value,
          taskReferences,
          `${path}.${key}`,
        ),
      0,
    );
  }

  return [];
};

export const findTaskReferencesInInputParameters = (
  existingTaskReferences: string[],
  task: Partial<TaskDef>,
): string[] => {
  const taskInputParameters = task?.inputParameters || {};
  return Object.entries(taskInputParameters).flatMap(([key, value]) =>
    valueContainsTaskReference(value, existingTaskReferences, key),
  );
};

export const findMissingJoinOnReferences = (
  task: TaskDef,
  existingTaskReferences: string[],
): string[] => {
  if (task.type === TaskType.JOIN && Array.isArray(task.joinOn)) {
    return task.joinOn.filter((ref) => !existingTaskReferences.includes(ref));
  }
  return [];
};

export const findUnMatchedTaskReferences = (
  existingTaskReferences: string[],
  possiblyAffectedTasks: TaskDef[],
): TaskWithUnknownReference[] => {
  return possiblyAffectedTasks.reduce(
    (acc: TaskWithUnknownReference[], task): TaskWithUnknownReference[] => {
      const parameters = findTaskReferencesInInputParameters(
        existingTaskReferences,
        task,
      );
      const expressions =
        validateExpressionWithInputParams(task as Partial<InlineTaskDef>) ?? [];

      const joinOn = findMissingJoinOnReferences(task, existingTaskReferences);

      if (_isEmpty(parameters) && _isEmpty(expressions) && _isEmpty(joinOn)) {
        return acc;
      }
      return acc.concat({
        task,
        parameters,
        expressions,
        joinOn,
      });
    },
    [],
  );
};

export const findUnMatchedWorkflowReferences = (
  existingTaskReferences: string[],
  workflow: Partial<WorkflowDef>,
) => {
  const workflowOutputParams = workflow?.outputParameters || {};

  const unMatchedWorkflowReferences = Object.entries(
    workflowOutputParams,
  ).reduce((acc: string[], [k, v]) => {
    const affectedParams = valueContainsTaskReference(
      v,
      existingTaskReferences,
      k,
    );
    if (_isEmpty(affectedParams)) {
      return acc;
    }
    return acc.concat(affectedParams);
  }, []);

  let refractoredArray: RefractorObject[] = [];
  if (workflow && workflow.outputParameters) {
    refractoredArray = findObjectWithValue(
      unMatchedWorkflowReferences,
      workflow,
    );
  }

  return refractoredArray;
};

export const findVariableReferencesInInputParameters = (
  variableTaskReferences: Record<string, string[]>,
  task: Partial<TaskDef>,
) => {
  const taskInputParameters = task?.inputParameters || {};
  const currentTaskVariable =
    (task.taskReferenceName &&
      variableTaskReferences[task?.taskReferenceName]) ||
    [];
  const possibleVariablePaths = currentTaskVariable.map(
    (variable) => `workflow.variables.${variable}`,
  );
  return Object.entries(taskInputParameters).flatMap(([key, value]) =>
    valueContainsVariableTaskReference(value, possibleVariablePaths, key),
  );
};

export const findUnMatchedVariableReferencesTaks = (
  variableTaskReferences: Record<string, string[]>,
  tasks: TaskDef[],
) => {
  return tasks.reduce(
    (acc: TaskWithUnknownReference[], task): TaskWithUnknownReference[] => {
      const parameters = findVariableReferencesInInputParameters(
        variableTaskReferences,
        task,
      );
      if (_isEmpty(parameters)) {
        return acc;
      }
      return acc.concat({
        task,
        parameters,
        expressions: [],
      });
    },
    [],
  );
};

function findObjectWithValue(arr: string[], obj: any) {
  const matchingKeys: RefractorObject[] = [];
  for (const [key, value] of _entries(obj?.outputParameters)) {
    if (arr.includes(key)) {
      matchingKeys.push({ [key]: value, workflowName: obj?.name });
    }
  }
  return matchingKeys;
}

const valueCrawler = (value: any, path = ""): string[] => {
  if (typeof value === "string") {
    return [path];
  }
  if (_isArray(value)) {
    return [path];
  }

  if (_isObject(value)) {
    const objResult = Object.entries(value).flatMap(
      ([key, value]) => valueCrawler(value, `${path}.${key}`),
      0,
    );
    return _isEmpty(objResult) ? [path] : objResult;
  }
  return [];
};

export const buildInputParameterNotationTree = (
  inputParameters: Record<string, unknown>,
  path: string,
): string[] => {
  return Object.entries(inputParameters).flatMap(([key, value]) =>
    valueCrawler(value, `${path}.${key}`),
  );
};

export const removedTasksToUnmatchedReferences = ({
  existingTaskReferences,
  lastTaskRoute,
}: TaskReferenceReportingParameters): Array<TaskWithUnknownReference> => {
  return findUnMatchedTaskReferences(existingTaskReferences, lastTaskRoute);
};

type TaskReferenceTaskTuple = [string[], TaskDef[]];

export const workflowParameterToValidationError = (
  obj: RefractorObject,
): ValidationError => {
  const firstKey = _first(Object.keys(obj));
  const reference = firstKey ? firstKey : "";
  const variableName = obj[reference] ? obj[reference] : "";
  const errorKind = variableName.includes("workflow") ? "workflow" : "task";
  const message = `'${firstKey ? firstKey : ""}' references unknown ${
    errorKind === "workflow"
      ? `workflow variable - '${variableName}'`
      : `task variable - '${variableName}'`
  }`;
  const legacyRefWarning = `'${
    firstKey ? firstKey : ""
  }' references '${variableName}', is a legacy ref and should be replaced by 'task_ref_name.taskId'`;
  return {
    id: ErrorIds.REFERENCE_PROBLEMS,
    message: variableName === `\${CPEWF_TASK_ID}` ? legacyRefWarning : message,
    type: ErrorTypes.WORKFLOW,
    severity: ErrorSeverity.WARNING,
  };
};

export const createJoinOnReferenceError = (
  task: TaskDef,
  missingRef: string,
) => {
  return {
    id: ErrorIds.REFERENCE_PROBLEMS,
    taskReferenceName: task.taskReferenceName,
    message: `joinOn references missing taskReferenceName '${missingRef}'`,
    type: ErrorTypes.TASK,
    severity: ErrorSeverity.WARNING,
  };
};

export const taskReferenceProblemToTaskErrors = (
  tp: TaskWithUnknownReference,
): TaskErrors => {
  const values = tp.parameters?.map((param) => {
    const path = param
      .split(".")
      .reduce((obj: any, key) => obj?.[key], tp?.task?.inputParameters);
    return path;
  });
  const parameterErrors = (tp.parameters || []).map((p, i) => ({
    id: ErrorIds.REFERENCE_PROBLEMS,
    taskReferenceName: tp.task.taskReferenceName,
    message:
      values[i] === `\${CPEWF_TASK_ID}`
        ? `input parameter '${p}' references '\${CPEWF_TASK_ID}', A legacy ref and should be replaced by 'task_ref_name.taskId'`
        : `input parameter '${p}' references non existing variable`,
    type: ErrorTypes.TASK,
    severity: ErrorSeverity.WARNING,
  }));
  const joinOnErrors = (tp.joinOn || []).map((missingRef) =>
    createJoinOnReferenceError(tp.task, missingRef),
  );
  return {
    task: tp.task,
    errors: [...parameterErrors, ...joinOnErrors],
  };
};

export const expressionReferenceProblemToInputParametersErrors = (
  tp: TaskWithUnknownReference,
): TaskErrors => {
  return {
    task: tp.task,
    errors: tp.expressions.map((p) => ({
      id: ErrorIds.REFERENCE_PROBLEMS,
      taskReferenceName: tp.task.taskReferenceName,
      message:
        tp.task.type === TaskType.JDBC
          ? `'statement' ${p}`
          : `expression input parameter '${p}' does not exist`,
      type: ErrorTypes.TASK,
      severity: ErrorSeverity.WARNING,
    })),
  };
};

export const testForRemovedTaskReferencesService = async (
  context: ErrorInspectorMachineContext,
): Promise<ReferenceProblems> => {
  const { currentWf: workflow, crumbMap, secrets, envs } = context;
  try {
    const [existingTaskReferences, tasks] = Object.entries(crumbMap!).reduce(
      (
        acc: TaskReferenceTaskTuple,
        [taskReferenceName, { task }],
      ): TaskReferenceTaskTuple => {
        const taskReferences: string[] = acc[0].concat(taskReferenceName);
        const cTask: TaskDef[] = acc[1].concat(task);
        const rTuple: TaskReferenceTaskTuple = [taskReferences, cTask];
        return rTuple;
      },
      [[], []],
    );

    const possibleWfParametesPath = (workflow?.inputParameters || []).map(
      (p) => `workflow.input.${p}`,
    );
    const envNames = Object.keys(envs || {});

    const secretNames = (secrets || []).map(
      (item: Record<string, unknown>) => item?.name,
    );
    const possibleSecretsNamePath = (secretNames || []).map(
      (p) => `workflow.secrets.${p}`,
    );

    const possibleEnvPaths = (envNames || []).map((p) => `workflow.env.${p}`);

    const basicValidation = () => {
      return existingTaskReferences
        .concat("workflow.secrets")
        .concat("workflow.env")
        .concat("workflow.input")
        .concat(DEFAULT_WF_ATTRIBUTES);
    };
    const advancedValidation = () => {
      return existingTaskReferences
        .concat(possibleWfParametesPath)
        .concat(possibleEnvPaths)
        .concat(possibleSecretsNamePath)
        .concat(DEFAULT_WF_ATTRIBUTES);
    };

    const possibleReferences = enabledAdvancedValidations
      ? advancedValidation()
      : basicValidation();

    const unMatchedTasks = removedTasksToUnmatchedReferences({
      existingTaskReferences: possibleReferences,
      lastTaskRoute: tasks,
    });

    const unMatchedTasksForVariable = findUnMatchedVariableReferencesTaks(
      getVariablesForEachTasks(crumbMap!),
      tasks,
    );

    const unMatchesInWorkflow = findUnMatchedWorkflowReferences(
      possibleReferences,
      workflow!,
    );

    const unMatchedReferenceTasks = [
      ...unMatchedTasks,
      ...(enabledAdvancedValidations ? unMatchedTasksForVariable : []),
    ];

    const expressionInputRefProblems = unMatchedReferenceTasks.reduce(
      (acc, task) => {
        const mapped: TaskErrors =
          expressionReferenceProblemToInputParametersErrors(task);
        if (mapped.errors.length > 0) {
          acc.push(mapped);
        }
        return acc;
      },
      [] as TaskErrors[],
    );

    const taskRefProblems = unMatchedReferenceTasks.reduce((acc, task) => {
      const mapped: TaskErrors = taskReferenceProblemToTaskErrors(task);
      if (mapped.errors.length > 0) {
        acc.push(mapped);
      }
      return acc;
    }, [] as TaskErrors[]);

    const consolidatedTaskReferenceProblems = [
      ...taskRefProblems,
      ...expressionInputRefProblems,
    ];

    return Promise.resolve({
      workflowReferenceProblems: unMatchesInWorkflow.map(
        workflowParameterToValidationError,
      ),
      taskReferencesProblems: consolidatedTaskReferenceProblems,
      unreachableTaskProblems: [],
    });
  } catch {
    return {
      workflowReferenceProblems: [],
      taskReferencesProblems: [],
      unreachableTaskProblems: [],
    };
  }
};

export const fetchSecrets = async ({
  authHeaders: headers,
}: ErrorInspectorMachineContext) => {
  const url = `/secrets-v2`;
  try {
    const result = await queryClient.fetchQuery([fetchContext.stack, url], () =>
      fetchWithContext(url, fetchContext, { headers }),
    );
    return result;
  } catch (error) {
    return Promise.reject(error);
  }
};

export const fetchSecretsEndEnvironmentsList = async (
  context: ErrorInspectorMachineContext,
) => {
  const secrets = enabledAdvancedValidations ? await fetchSecrets(context) : [];
  const envs = enabledAdvancedValidations
    ? await getEnvVariables({ authHeaders: context.authHeaders! })
    : [];
  return { secrets, envs };
};

export function isValidNestedVariable(
  arrayOfStrings: string[],
  valueString: string,
  variables?: string[],
): boolean {
  // regex to check valid nested variables
  const regex = /\${(?:[a-zA-Z0-9_.\-\\[\]]|\$\{[a-zA-Z0-9_.\-\\[\]]+\})+}/g;

  return (
    (valueString.match(regex) !== null &&
      valueString.match(regex)?.every((match) => {
        const variablesFromMatch =
          _nth(match.substring(2, match.length - 1).split(".${"), 0) ?? "";

        const innerValue = match.substring(2, match.length - 1);
        if (innerValue.includes(".${")) {
          return isValidNestedVariable(arrayOfStrings, innerValue, [
            variablesFromMatch,
          ]);
        }
        const parts = [...(variables ?? []), variablesFromMatch];
        const [outermostParent, ...children] = [...parts];
        return (
          outermostParent === "workflow.secrets" &&
          children.every((item) => arrayOfStrings.includes(item))
        );
      })) ??
    false
  );
}
