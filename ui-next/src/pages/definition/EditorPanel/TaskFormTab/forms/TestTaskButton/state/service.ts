import { tryFunc, tryToJson } from "utils/utils";
import { TestTaskButtonMachineContext } from "./types";
import { fetchWithContext, fetchContextNonHook } from "plugins/fetch";
import { queryClient } from "queryClient";
import { logger } from "utils/logger";
import { TaskType } from "types/common";
import { getCorrespondingJoinTask } from "../../../helpers";

const fetchContext = fetchContextNonHook();

export const runTestTask = async ({
  authHeaders,
  originalTask,
  user,
  taskDomain,
  taskChanges,
  tasksList,
}: TestTaskButtonMachineContext) => {
  const suffix = Math.random().toString(36).substring(2, 9);
  const name = `test_task_${originalTask?.name}_${suffix}`;

  const originalTaskIsFork = originalTask?.type === TaskType.FORK_JOIN;
  const originalTaskIsDynamicFork =
    originalTask?.type === TaskType.FORK_JOIN_DYNAMIC;

  const workflowWithTask = {
    name: name,
    version: 1,
    workflowDef: {
      name: name,
      description: `Test ${originalTask?.type} task within a workflow`,
      version: 1,
      tasks: [
        {
          ...originalTask,
          inputParameters:
            taskChanges && tryToJson(JSON.stringify(taskChanges)),
        },
        ...(originalTaskIsFork || originalTaskIsDynamicFork
          ? getCorrespondingJoinTask(originalTask ?? {}, tasksList)
          : []),
      ],
      createdBy: user?.id || "example@email.com",
    },
    ...(taskDomain
      ? {
          taskToDomain: {
            [`${originalTask?.taskReferenceName}`]: taskDomain,
          },
        }
      : {}),
  };

  const body = JSON.stringify(workflowWithTask, null, 0);

  return tryFunc({
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

export const pollForExecutionResult = async ({
  authHeaders: headers,
  testExecutionId,
}: TestTaskButtonMachineContext) => {
  const url = `/workflow/${testExecutionId}?summarize=true`;
  try {
    const result = await queryClient.fetchQuery([fetchContext.stack, url], () =>
      fetchWithContext(url, fetchContext, { headers }),
    );
    return result;
  } catch (error) {
    logger.error("Fetching task list page", error);
    return Promise.reject({ message: "Error fetching task list page" });
  }
};
