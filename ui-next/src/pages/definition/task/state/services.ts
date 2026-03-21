import { queryClient } from "queryClient";
import { fetchWithContext, fetchContextNonHook } from "plugins/fetch";
import { TaskDefinitionMachineContext } from "pages/definition/task/state/types";
import {
  exportObjToFile,
  getErrors,
  tryToJson,
  featureFlags,
  FEATURES,
  tryFunc,
  logger,
} from "utils";
import { TaskDefinitionDto } from "types/TaskDefinition";
import { ErrorObj } from "types/common";

const taskVisibility = featureFlags.getValue(FEATURES.TASK_VISIBILITY, "READ");
const fetchContext = fetchContextNonHook();

export const fetchTaskDefinitionsService = async ({
  authHeaders: headers,
}: TaskDefinitionMachineContext) => {
  const taskDefinitionsPath = `/metadata/taskdefs?access=${taskVisibility}`;

  try {
    const response = await queryClient.fetchQuery(
      [fetchContext.stack, taskDefinitionsPath],
      () => fetchWithContext(taskDefinitionsPath, fetchContext, { headers }),
    );
    return response;
  } catch (error) {
    const errorDetail = await getErrors(error as Response);

    return Promise.reject({
      message: errorDetail.message
        ? errorDetail.message
        : "Fetching task definitions failed!",
    });
  }
};

export const fetchTaskDefinitionByNameService = async ({
  authHeaders: headers,
  originTaskDefinition,
}: TaskDefinitionMachineContext) => {
  const taskDefinitionPath = `/metadata/taskdefs/${originTaskDefinition.name}`;

  return tryFunc<TaskDefinitionDto, ErrorObj>({
    fn: async () => {
      return await queryClient.fetchQuery(
        [fetchContext.stack, taskDefinitionPath],
        () => fetchWithContext(taskDefinitionPath, fetchContext, { headers }),
      );
    },
    customError: {
      message: "Fetching task definition by name failed!",
    },
    showCustomError: false,
  });
};

const validateTaskName = (taskName?: string) => {
  if (taskName?.includes(":")) {
    return Promise.reject({
      message: 'Task name should not include the colon character ":"',
    });
  }
  return null;
};

export const createTaskDefinitionService = async ({
  authHeaders,
  modifiedTaskDefinition,
}: TaskDefinitionMachineContext) => {
  const validationError = validateTaskName(modifiedTaskDefinition.name);

  if (validationError) {
    return validationError;
  }

  const stringDefinition = JSON.stringify(modifiedTaskDefinition, null, 2);
  const body = `[${stringDefinition}]`;

  return tryFunc<TaskDefinitionDto, ErrorObj>({
    fn: async () => {
      return await fetchWithContext(
        "/metadata/taskdefs",
        {},
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...authHeaders,
          },
          body,
        },
      );
    },
    customError: {
      message: "Create a new task fail!",
    },
    showCustomError: false,
  });
};

export const updateTaskDefinitionService = async ({
  authHeaders,
  modifiedTaskDefinition,
}: TaskDefinitionMachineContext) => {
  const validationError = validateTaskName(modifiedTaskDefinition.name);

  if (validationError) {
    return validationError;
  }

  const stringDefinition = JSON.stringify(modifiedTaskDefinition, null, 2);

  return tryFunc<TaskDefinitionDto, ErrorObj>({
    fn: async () => {
      return await fetchWithContext(
        "/metadata/taskdefs",
        {},
        {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
            ...authHeaders,
          },
          body: stringDefinition,
        },
      );
    },
    customError: {
      message: "Update task failed!",
    },
    showCustomError: false,
  });
};

export const createOrUpdateTaskDefinitionService = async (
  context: TaskDefinitionMachineContext,
) => {
  const taskChangedName =
    context.modifiedTaskDefinition.name !== context.originTaskDefinition.name;
  return context.isNewTaskDef || taskChangedName
    ? createTaskDefinitionService(context)
    : updateTaskDefinitionService(context);
};

export const deleteTaskDefinitionService = async ({
  authHeaders,
  originTaskDefinition,
}: TaskDefinitionMachineContext) => {
  if (!originTaskDefinition.name) {
    return Promise.reject({ message: "Task's name is undefined" });
  }

  const taskDefinitionPath = `/metadata/taskdefs/${encodeURIComponent(
    originTaskDefinition.name,
  )}`;

  return tryFunc<TaskDefinitionDto, ErrorObj>({
    fn: async () => {
      return await fetchWithContext(
        taskDefinitionPath,
        {},
        {
          method: "DELETE",
          headers: {
            "Content-Type": "application/json",
            ...authHeaders,
          },
        },
      );
    },
    customError: {
      message: "Delete task failed!",
    },
    showCustomError: false,
  });
};

export const runTestTaskService = async ({
  authHeaders,
  modifiedTaskDefinition,
  user,
  testTaskDomain,
  testInputParameters,
}: TaskDefinitionMachineContext) => {
  // generate random string of six characters
  const suffix = Math.random().toString(36).substring(2, 7);
  if (modifiedTaskDefinition?.name == null) {
    logger.error("Task name is null");
    return Promise.reject({
      message: "Task name is null",
    });
  }

  const workflowWithTask = {
    name: `test_task_{${modifiedTaskDefinition.name}}_${suffix}_wf`,
    version: 1,
    workflowDef: {
      name: `TestTask_${modifiedTaskDefinition.name}_${suffix}`,
      description: `Dynamic workflow to test the task: [${modifiedTaskDefinition.name}]`,
      version: 1,
      tasks: [
        {
          name: modifiedTaskDefinition.name,
          taskReferenceName: `test_task_${modifiedTaskDefinition.name}_${suffix}`,
          type: "SIMPLE",
          inputParameters:
            testInputParameters && tryToJson(testInputParameters),
        },
      ],
      createdBy: user?.id || "example@email.com",
    },
    ...(testTaskDomain
      ? {
          taskToDomain: {
            [modifiedTaskDefinition.name]: testTaskDomain,
          },
        }
      : {}),
  };

  const body = JSON.stringify(workflowWithTask, null, 0);

  return tryFunc<TaskDefinitionDto, ErrorObj>({
    fn: async () => {
      return await fetchWithContext(
        "/workflow",
        {},
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...authHeaders,
          },
          body,
        },
        true,
      );
    },
    customError: {
      message: "Run test task failed.",
    },
    showCustomError: false,
  });
};

export const handleDownloadFile = async ({
  modifiedTaskDefinition,
}: TaskDefinitionMachineContext) => {
  try {
    exportObjToFile({
      data: modifiedTaskDefinition,
      fileName: `${modifiedTaskDefinition.name || "new"}.json`,
      type: `application/json`,
    });
  } catch (error: any) {
    const errorDetail = await getErrors(error as Response);

    return Promise.reject({
      message: errorDetail.message
        ? errorDetail.message
        : "Download task failed!",
    });
  }
};
