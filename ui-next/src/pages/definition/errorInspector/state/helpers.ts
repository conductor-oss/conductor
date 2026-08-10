import { NodeData } from "reaflow";
import _last from "lodash/last";
import {
  TaskDef,
  TaskType,
  Crumb,
  CrumbMap,
  InlineTaskDef,
  DoWhileTaskDef,
  JoinTaskDef,
  SwitchTaskDef,
  JDBCTaskDef,
  WorkflowDef,
} from "types";
import {
  extractVariablesFromTask,
  undeclaredInputParameters,
} from "pages/definition/helpers";
import {
  ServerValidationError,
  StoredValidationError,
  ValidationError,
} from "./types";
import _nth from "lodash/nth";
import _path from "lodash/fp/path";

import fastDeepEqual from "fast-deep-equal";
import { NodeTaskData } from "components/features/flow/nodes/mapper";
export type NodeInnerData = { task: TaskDef; crumbs: Crumb[] };
type SingleEntry = [string, NodeInnerData];
type EntriesIgnoreSubWorkflowChilds = {
  entries: Array<SingleEntry>;
  subWorkflowTaskReferences: string[];
};

export const nodesToCrumbMap = (nodes: NodeData<NodeInnerData>[]): CrumbMap => {
  const entrieWithoutSubWorkflowSubTasks = nodes.reduce(
    (acc: EntriesIgnoreSubWorkflowChilds, { id, data, parent }) => {
      const taskType = data?.task?.type;
      if (taskType === "SWITCH_JOIN") {
        return acc;
      }
      const possibleEntry = [
        [id, { task: data!.task as TaskDef, crumbs: data!.crumbs as Crumb[] }],
      ];
      if (taskType === TaskType.SUB_WORKFLOW) {
        // If subworkflow extract possible parent
        return {
          entries: acc.entries.concat(possibleEntry as unknown as SingleEntry), // TS does not seem to know that if a concat an array it will just join it
          subWorkflowTaskReferences: acc.subWorkflowTaskReferences.concat(id),
        };
      }
      if (
        (parent && acc.subWorkflowTaskReferences.includes(parent)) ||
        id === "start" ||
        id === "end"
      ) {
        // if parent is included ignore node and crumbs. we arent drilling on subworkflows so we are safe
        return acc;
      }
      return {
        entries: acc.entries.concat(possibleEntry as unknown as SingleEntry),
        subWorkflowTaskReferences: acc.subWorkflowTaskReferences,
      };
    },
    { entries: [], subWorkflowTaskReferences: [] },
  );
  return Object.fromEntries(entrieWithoutSubWorkflowSubTasks.entries);
};

const invalidVariables = (codeExpression: string, givenVariables: string[]) => {
  const invalidParameters = givenVariables.map((word: string) => {
    const wordRegex = new RegExp(`${word}(?=[^a-zA-Z0-9_$]|$)`, "g");
    const decorators = [];
    let _match;
    while ((_match = wordRegex.exec(codeExpression))) {
      decorators.push(word);
    }
    return decorators;
  });
  return invalidParameters ? invalidParameters.flat() : [];
};

export const validateExpressionWithInputParams = (
  task:
    | Partial<InlineTaskDef>
    | Partial<DoWhileTaskDef>
    | Partial<SwitchTaskDef>
    | Partial<JoinTaskDef>
    | Partial<JDBCTaskDef>,
) => {
  if (task.type === TaskType.INLINE) {
    const taskExpression = task?.inputParameters?.expression ?? "";
    const addedInputParameters = undeclaredInputParameters(
      taskExpression,
      task?.inputParameters,
    );
    return invalidVariables(taskExpression, addedInputParameters);
  }
  if (task.type === TaskType.DO_WHILE) {
    const taskExpression = task?.loopCondition ?? "";
    const taskReferenceName = task?.taskReferenceName ?? "";
    const addedInputParameters = undeclaredInputParameters(
      taskExpression,
      task?.inputParameters,
    );
    if (addedInputParameters.includes(taskReferenceName)) {
      addedInputParameters.splice(
        addedInputParameters.indexOf(taskReferenceName),
        1,
      );
    }
    const loopOverTasks =
      task?.loopOver?.map((item) => item.taskReferenceName) ?? [];
    const filteredAddedInputParameters = addedInputParameters.filter(
      (element) => !loopOverTasks.includes(element),
    );
    return invalidVariables(taskExpression, filteredAddedInputParameters);
  }
  if (task.type === TaskType.SWITCH) {
    const taskExpression = task?.expression ?? "";
    const addedInputParameters = undeclaredInputParameters(
      taskExpression,
      task?.inputParameters,
    );
    return invalidVariables(taskExpression, addedInputParameters);
  }
  if (task.type === TaskType.JOIN) {
    const taskExpression = task?.expression ?? "";
    const addedInputParameters = undeclaredInputParameters(
      taskExpression,
      task?.inputParameters,
    );
    let filteredInputParameters = [...addedInputParameters];
    if (addedInputParameters.includes("joinOn")) {
      filteredInputParameters = addedInputParameters.filter(
        (item) => item !== "joinOn",
      );
    }
    return invalidVariables(taskExpression, filteredInputParameters);
  }

  if (task.type === TaskType.JDBC) {
    const taskExpression = task?.inputParameters?.statement ?? "";
    const numberQuestionCharacters = (taskExpression.match(/\?/g) || []).length;
    const parameters = task?.inputParameters?.parameters ?? [];

    const isValidParameters = numberQuestionCharacters === parameters.length;

    return isValidParameters
      ? []
      : [
          `JDBC task should have ${numberQuestionCharacters} query parameter${
            numberQuestionCharacters > 1 ? "s" : ""
          }`,
        ];
  }
};

export const getVariablesForEachTasks = (
  crumbMaps: CrumbMap,
): Record<string, string[]> => {
  const referencesForTaskKeys: Record<string, string[]> = {};
  Object.entries(crumbMaps).forEach(([key, value]) => {
    const tasks = value.crumbs
      .map((crumb) => crumb.ref)
      .map((ref) => crumbMaps[ref].task);

    const lastTask = _last(tasks);
    if (lastTask?.type === TaskType.SET_VARIABLE) {
      tasks.pop();
    }
    referencesForTaskKeys[key] = extractVariablesFromTask(tasks);
  });
  return referencesForTaskKeys;
};

export const jakatraPathToPropertyPath = (path?: string): string => {
  if (!path) return "";

  // Extract everything after 'tasks' including the tasks part
  const tasksAndAfter = path.split("tasks").pop();
  if (!tasksAndAfter) return "";

  return (
    tasksAndAfter
      // Remove any prefix like update.workflowDefs[0]
      .replace(/^.*?tasks/, "tasks")
      // Remove <list element> markers
      .replace(/<list element>/g, "")
      // Remove <map value> markers
      .replace(/<map value>/g, "")
      // Clean up any double brackets that might have been created
      .replace(/\]\[/g, "][")
      // Remove any dots that appear right before a bracket
      .replace(/\.\[/g, "[")
  );
};

export const serverValidationErrorToIndexTask = (
  validationErrors: ServerValidationError[],
  workflowTasks: TaskDef[],
): StoredValidationError[] => {
  return validationErrors.map((sve) => {
    const { path } = sve;
    const maybeTaskPath = jakatraPathToPropertyPath(path);
    if (maybeTaskPath != null) {
      const valAtIdx = _path(maybeTaskPath, workflowTasks);
      return valAtIdx != null
        ? {
            ...sve,
            taskPath: maybeTaskPath,
            task: valAtIdx,
          }
        : sve;
    }
    return sve;
  });
};

export const reverifyServerErrorsTaskChanges = (
  serverErrors: ValidationError[],
  currentWorkflow: Partial<WorkflowDef>,
): ValidationError[] | undefined => {
  const serverError = _nth(serverErrors, 0);
  if (serverError != null) {
    const validationErrors =
      serverError.validationErrors
        ?.map((sve) => {
          if (sve.path != null && sve?.taskPath == null) return []; // Any change to the workflow means the error is not valid anymore
          if (!sve?.taskPath) return sve;
          const updatedTask = _path(sve?.taskPath, currentWorkflow.tasks);
          if (updatedTask && !fastDeepEqual(sve.task, updatedTask)) {
            // task is not the same remove validation
            return [];
          }
          return sve;
        })
        .flat() ?? [];

    return validationErrors[0] === undefined
      ? undefined
      : [{ ...serverError, validationErrors }];
  }
};

export const filterServerErrorsNotPresentInNodes = (
  serverErrors: ValidationError[],
  nodes: NodeData<NodeTaskData<TaskDef>>[],
) => {
  const serverError = _nth(serverErrors, 0);
  if (serverError != null) {
    const validationErrors =
      serverError.validationErrors
        ?.map((sve) => {
          if (sve?.task == null) return sve;
          const targetNode = nodes.find(
            (n) =>
              n.data?.task.taskReferenceName === sve.task?.taskReferenceName,
          );
          if (targetNode == null) {
            return []; // Node still exist means no changes
          }

          return sve;
        })
        .flat() ?? [];

    return validationErrors[0] === undefined
      ? undefined
      : [{ ...serverError, validationErrors }];
  }
};
