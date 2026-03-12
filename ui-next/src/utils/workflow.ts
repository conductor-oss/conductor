import { WorkflowDef } from "types/WorkflowDef";
import _sortBy from "lodash/sortBy";
import _uniqBy from "lodash/fp/uniqBy";
import _uniq from "lodash/fp/uniq";
import {
  CommonTaskDef,
  ForkJoinTaskDef,
  SwitchTaskDef,
  DoWhileTaskDef,
  SetVariableTaskDef,
  ForkJoinDynamicDef,
} from "types/TaskType";
import {
  isDynamicForkTask,
  isHumanTask,
  isJDBCTask,
  isLLMTask,
  isSetVariable,
  isTask,
} from "./task";
import { isObjectOnlyNotArray, isObjectOrArray } from "./object";

/**
 * Get unique workflows with latest version
 * @param workflows WorkflowDef[]
 * @returns WorkflowDef[]
 */
export const getUniqueWorkflows = (workflows: WorkflowDef[]) => {
  const unique = new Map();
  const types = new Set();

  for (const workflowDef of workflows) {
    if (!workflowDef.createTime) {
      workflowDef.createTime = 0;
    }

    if (!unique.has(workflowDef.name)) {
      unique.set(workflowDef.name, workflowDef);
    } else if (unique.get(workflowDef.name).version < workflowDef.version) {
      unique.set(workflowDef.name, workflowDef);
    }

    if (workflowDef.tasks) {
      for (const task of workflowDef.tasks) {
        types.add(task.type);
      }
    }
  }

  return Array.from(unique.values());
};

/**
 * Get unique workflows with versions
 * @param workflows WorkflowDef[]
 * @returns Map<string, number[]>
 */
export const getUniqueWorkflowsWithVersions = (workflows?: WorkflowDef[]) => {
  const result = new Map<string, number[]>();

  if (workflows) {
    for (const def of workflows) {
      let arr: number[] = result.get(def.name) || [];

      if (!result.has(def.name)) {
        arr = [];
        result.set(def.name, arr);
      }

      arr.push(def.version);
    }

    // Sort arrays in place
    result.forEach((val, key) => {
      // Sort versions
      result.set(key, _sortBy(val));
    });
  }

  return result;
};

const isForkJoin = (task: CommonTaskDef): task is ForkJoinTaskDef =>
  task.type === "FORK_JOIN";
const isSwitch = (task: CommonTaskDef): task is SwitchTaskDef =>
  task.type === "SWITCH";
const isDoWhile = (task: CommonTaskDef): task is DoWhileTaskDef =>
  task.type === "DO_WHILE";

export function mapWalk(
  tasks: CommonTaskDef[],
  fn: (task: CommonTaskDef) => CommonTaskDef | null,
): CommonTaskDef[] {
  return tasks.flatMap((task) => {
    const newTask = fn(task);
    if (!newTask) {
      return [];
    }

    if (isForkJoin(newTask)) {
      newTask.forkTasks = newTask.forkTasks.map((tasks) => mapWalk(tasks, fn));
    }

    if (isSwitch(newTask)) {
      newTask.decisionCases = Object.fromEntries(
        Object.entries(newTask.decisionCases).map(([key, tasks]) => [
          key,
          mapWalk(tasks, fn),
        ]),
      );
      if (newTask.defaultCase) {
        newTask.defaultCase = mapWalk(newTask.defaultCase, fn);
      }
    }

    if (isDoWhile(newTask)) {
      newTask.loopOver = mapWalk(newTask.loopOver, fn);
    }

    return newTask;
  });
}

function* walk(tasks: CommonTaskDef[]): Generator<CommonTaskDef> {
  for (const task of tasks) {
    yield task;

    if (isForkJoin(task) && task.forkTasks) {
      for (const forkBranch of task.forkTasks) {
        yield* walk(forkBranch);
      }
    }

    if (isSwitch(task)) {
      if (task.decisionCases) {
        for (const caseTasks of Object.values(task.decisionCases)) {
          yield* walk(caseTasks);
        }
      }
      if (task.defaultCase) yield* walk(task.defaultCase);
    }

    if (isDoWhile(task) && task.loopOver) yield* walk(task.loopOver);
  }
}

// Flatten: returns a flat array of tasks
export function flatten(tasks: CommonTaskDef[]): CommonTaskDef[] {
  return [...walk(tasks)];
}

export function filterTasks(
  tasks: CommonTaskDef[],
  predicate: (task: CommonTaskDef) => boolean,
): CommonTaskDef[] {
  return Array.from(walk(tasks)).filter(predicate);
}

/// Task Predicates

const hasWorkflowVariable = (a: string): boolean => a.includes("${");
/// Move to utils
// Move to utils

const variableValueTaskParserExtractor = (
  task: SetVariableTaskDef | ForkJoinDynamicDef,
  extractor: (task: CommonTaskDef) => string[],
): string[] => {
  const taskValues = Object.values(task.inputParameters);
  const valuesThatAreObjectOrArrays = taskValues.filter((value) =>
    isObjectOrArray(value),
  );
  return valuesThatAreObjectOrArrays.flatMap((value) => {
    if (isObjectOnlyNotArray(value) && isTask(value)) {
      return extractor(value);
    } else if (Array.isArray(value)) {
      return value.flatMap((v) => extractor(v));
    }
    return [];
  });
};

export const handlebarsMatcherExtractor = (
  val: any,
  matcher: (val: string) => boolean,
): string[] => {
  if (val && typeof val === "string") {
    if (matcher(val)) {
      return [val];
    }
  }
  if (Array.isArray(val)) {
    return val.flatMap((v) => handlebarsMatcherExtractor(v, matcher));
  }
  if (isObjectOnlyNotArray(val)) {
    return Object.values(val).flatMap((v) =>
      handlebarsMatcherExtractor(v, matcher),
    );
  }
  return [];
};

const extractIntegrationName = (task: CommonTaskDef): string[] => {
  if (isLLMTask(task)) {
    if (
      "llmProvider" in task.inputParameters &&
      task.inputParameters.llmProvider != null &&
      !hasWorkflowVariable(task.inputParameters.llmProvider)
    ) {
      return [task.inputParameters.llmProvider];
    } else if (
      "vectorDB" in task.inputParameters &&
      task.inputParameters.vectorDB != null &&
      !hasWorkflowVariable(task.inputParameters.vectorDB)
    ) {
      return [task.inputParameters.vectorDB];
    }
  } else if (isSetVariable(task) || isDynamicForkTask(task)) {
    return variableValueTaskParserExtractor(task, extractIntegrationName);
  } else if (
    isJDBCTask(task) &&
    "integrationName" in task.inputParameters &&
    task.inputParameters.integrationName != null &&
    !hasWorkflowVariable(task.inputParameters.integrationName)
  ) {
    return [task.inputParameters.integrationName];
  }
  return [];
};

const extractPromptName = (task: CommonTaskDef): string[] => {
  if (isLLMTask(task)) {
    if (
      "promptName" in task.inputParameters &&
      task.inputParameters.promptName != null &&
      !hasWorkflowVariable(task.inputParameters.promptName)
    ) {
      return [task.inputParameters.promptName];
    }

    if (
      "instructions" in task.inputParameters &&
      task.inputParameters.instructions != null &&
      !hasWorkflowVariable(task.inputParameters.instructions)
    ) {
      return [task.inputParameters.instructions];
    }
  } else if (isSetVariable(task) || isDynamicForkTask(task)) {
    return variableValueTaskParserExtractor(task, extractPromptName);
  }
  return [];
};

export type NameVersion = {
  name: string;
  version?: string;
};

const extractUserFormNameVersion = (task: CommonTaskDef): NameVersion[] => {
  if (isHumanTask(task)) {
    if (task.inputParameters.__humanTaskDefinition.userFormTemplate?.name) {
      return [
        {
          name: task.inputParameters.__humanTaskDefinition.userFormTemplate
            .name,
          version:
            task.inputParameters.__humanTaskDefinition.userFormTemplate.version?.toString(),
        },
      ];
    }
  }
  return [];
};

const secretMatcher = (val: string) => {
  return val.includes("${workflow.secrets.") && val.includes("}");
};

const environmentVariablesMatcher = (val: string) => {
  return val.includes("${workflow.env.") && val.includes("}");
};

const extractSecrets = (task: CommonTaskDef): string[] => {
  return handlebarsMatcherExtractor(task, secretMatcher);
};

const extractEnvironmentVariables = (task: CommonTaskDef): string[] => {
  return handlebarsMatcherExtractor(task, environmentVariablesMatcher);
};

const extractSchemaNameVersion = (task: CommonTaskDef): NameVersion[] => {
  const schemas = [];
  if (task.taskDefinition?.inputSchema?.name) {
    schemas.push({
      name: task.taskDefinition?.inputSchema?.name,
      version: task.taskDefinition?.inputSchema?.version?.toString(),
    });
  }
  if (task.taskDefinition?.outputSchema?.name) {
    schemas.push({
      name: task.taskDefinition?.outputSchema?.name,
      version: task.taskDefinition?.outputSchema?.version?.toString(),
    });
  }
  return schemas;
};

type FoundDependencies = {
  integrationNames: Set<string>;
  promptNames: Set<string>;
  userFormsNameVersion: NameVersion[];
  schemas: NameVersion[];
  secrets: Set<string>;
  env: Set<string>;
};

const uniqueNameVersion = _uniqBy(
  (nv: NameVersion) => `${nv.name}:${nv.version || ""}`,
);

/**
 * Walks through all available tasks in search for dependencies
 *
 * @param tasks wokflow tasks
 * @returns
 */
export function scanTasksForDependenciesInTasks(tasks: CommonTaskDef[]) {
  const extractedDependencies = Array.from(
    walk(tasks),
  ).reduce<FoundDependencies>(
    (acc: FoundDependencies, task): FoundDependencies => {
      const integrationNames = extractIntegrationName(task);
      if (integrationNames && integrationNames.length > 0) {
        integrationNames.forEach((name) => acc.integrationNames.add(name));
      }
      const promptNames = extractPromptName(task);
      if (promptNames && promptNames.length > 0) {
        promptNames.forEach((name) => acc.promptNames.add(name));
      }

      const secrets = extractSecrets(task);
      secrets.forEach((s) => acc.secrets.add(s));

      const env = extractEnvironmentVariables(task);
      env.forEach((ev) => acc.env.add(ev));

      const userForms = extractUserFormNameVersion(task);

      const schemas = extractSchemaNameVersion(task);

      return {
        ...acc,
        userFormsNameVersion: acc.userFormsNameVersion.concat(userForms),
        schemas: acc.schemas.concat(schemas),
      };
    },
    {
      integrationNames: new Set<string>(),
      promptNames: new Set<string>(),
      userFormsNameVersion: [],
      secrets: new Set<string>(),
      schemas: [],
      env: new Set<string>(),
    },
  );
  return {
    integrationNames: Array.from(extractedDependencies.integrationNames),
    promptNames: Array.from(extractedDependencies.promptNames),
    userFormsNameVersion: uniqueNameVersion(
      extractedDependencies.userFormsNameVersion,
    ),
    schemas: uniqueNameVersion(extractedDependencies.schemas),
    secrets: Array.from(extractedDependencies.secrets),
    env: Array.from(extractedDependencies.env),
  } as const;
}

export function scanTasksForDependenciesInWorkflow(workflow: WorkflowDef) {
  const taskDependencies = scanTasksForDependenciesInTasks(
    workflow.tasks || [],
  );

  const workflowSchema: NameVersion[] = [];

  if (workflow.inputSchema?.name) {
    workflowSchema.push({
      name: workflow.inputSchema.name as string,
      version: workflow.inputSchema?.version?.toString(),
    });
  }

  if (workflow.outputSchema?.name) {
    workflowSchema.push({
      name: workflow.outputSchema.name as string,
      version: workflow.outputSchema?.version?.toString(),
    });
  }

  const workflowSecrets = handlebarsMatcherExtractor(
    workflow?.outputParameters ?? {},
    secretMatcher,
  );

  const workflowEnv = handlebarsMatcherExtractor(
    workflow?.outputParameters ?? {},
    environmentVariablesMatcher,
  );

  return {
    ...taskDependencies,
    schemas: uniqueNameVersion(taskDependencies.schemas.concat(workflowSchema)),
    secrets: _uniq(taskDependencies.secrets.concat(workflowSecrets)),
    env: _uniq(taskDependencies.env.concat(workflowEnv)),
    workflowName: workflow.name,
    workflowVersion: workflow.version,
  };
}

export const replaceIntegrationName = (
  task: CommonTaskDef,
  originalName: string,
  replaceName: string,
): CommonTaskDef => {
  if (isLLMTask(task)) {
    const newInputParameters = { ...task.inputParameters };
    let changed = false;

    if (
      "llmProvider" in newInputParameters &&
      newInputParameters.llmProvider === originalName &&
      !hasWorkflowVariable(newInputParameters.llmProvider)
    ) {
      newInputParameters.llmProvider = replaceName;
      changed = true;
    } else if (
      "vectorDB" in newInputParameters &&
      newInputParameters.vectorDB === originalName &&
      !hasWorkflowVariable(newInputParameters.vectorDB)
    ) {
      newInputParameters.vectorDB = replaceName;
      changed = true;
    }

    if (changed) {
      return { ...task, inputParameters: newInputParameters } as typeof task;
    }
    return task;
  } else if (isSetVariable(task) || isDynamicForkTask(task)) {
    // Recursively replace in inputParameters
    const newInputParameters = { ...task.inputParameters };
    let changed = false;
    for (const key of Object.keys(newInputParameters)) {
      const value = newInputParameters[key as keyof typeof newInputParameters];
      if (isObjectOnlyNotArray(value) && isTask(value)) {
        const replaced = replaceIntegrationName(
          value,
          originalName,
          replaceName,
        );
        if (replaced !== value) {
          newInputParameters[key as keyof typeof newInputParameters] = replaced;
          changed = true;
        }
      } else if (Array.isArray(value)) {
        const replacedArr = value.map((v) =>
          isTask(v) ? replaceIntegrationName(v, originalName, replaceName) : v,
        );
        if (JSON.stringify(replacedArr) !== JSON.stringify(value)) {
          newInputParameters[key as keyof typeof newInputParameters] =
            replacedArr;
          changed = true;
        }
      }
    }
    if (changed) {
      return { ...task, inputParameters: newInputParameters } as typeof task;
    }
    return task;
  }
  return task;
};
