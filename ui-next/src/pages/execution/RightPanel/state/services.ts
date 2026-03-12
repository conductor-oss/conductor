import { RightPanelContext, UpdateSelectedTaskStatus } from "./types";
import { fetchWithContext } from "plugins/fetch";
import { getErrors } from "utils";

// This was rolled back since it does not work for COMPLETED workflows

/* export const fetchForTaskDetailsService = async ({ */
/*   taskId, */
/*   authHeaders: headers, */
/* }: RightPanelContext) => { */
/*   const url = `tasks/${taskId}`; */
/*   const result = await queryClient.fetchQuery([fetchContext.stack, url], () => */
/*     fetchWithContext(url, fetchContext, { headers }) */
/*   ); */
/*   return result; */
/* }; */

export const updateTaskState = async (
  { executionId, selectedTask, authHeaders }: RightPanelContext,
  event: any,
) => {
  const {
    payload: { status, body = {} },
  } = event as UpdateSelectedTaskStatus;
  const { referenceTaskName } = selectedTask!;
  const url = `/tasks/${executionId}/${referenceTaskName}/${status}?workerid=conductor-ui`;
  try {
    const result = await fetchWithContext(
      url,
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
    return result;
  } catch (error) {
    const errorDetails = await getErrors(error as Response);
    return Promise.reject({ originalError: error, errorDetails });
  }
};

export const fetchTaskLogs = async ({
  authHeaders,
  selectedTask,
}: RightPanelContext) => {
  if (selectedTask?.taskId === undefined) {
    return Promise.reject({
      originalError: "No Selected Task",
      errorDetails: { message: "No Selected Task" },
    });
  }
  const path = `/tasks/${selectedTask?.taskId}/log`;
  try {
    const result = await fetchWithContext(
      path,
      {},
      {
        method: "GET",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
      },
    );
    return result;
  } catch (error) {
    return Promise.reject(error);
  }
};
export const reRunWoflowFromTask = async ({
  authHeaders,
  executionId,
  selectedTask,
}: RightPanelContext) => {
  if (!selectedTask) {
    return Promise.reject({
      originalError: "No Selected Task",
      errorDetails: { message: "No Selected Task" },
    });
  }
  const url = `/workflow/${executionId}/rerun`;
  try {
    const result = await fetchWithContext(
      url,
      {},
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
        body: JSON.stringify({
          reRunFromTaskId: selectedTask.taskId,
        }),
      },
      true,
    );
    return result;
  } catch (error) {
    const errorDetails = await getErrors(error as Response);
    return Promise.reject({ originalError: error, errorDetails });
  }
};
