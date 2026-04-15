import { tryToJson, tryFunc } from "utils/utils";
import { RunMachineContext } from "./types";
import { fetchWithContext } from "plugins/fetch";
import { v4 as uuidv4 } from "uuid";
const GENERIC_ERROR_MESSAGE = "Error while running workflow.";

export const runWorkflow = async (
  {
    authHeaders,
    input: inputParams,
    taskToDomain: tasksToDomain,
    correlationId,
    currentWf,
    idempotencyKey,
    idempotencyStrategy,
  }: RunMachineContext,
  __: any,
) => {
  return tryFunc({
    fn: async () => {
      const RECORD_LIMIT = 20;
      const input = tryToJson(inputParams);
      const taskToDomain = tryToJson(tasksToDomain);
      const postObject = {
        name: currentWf?.name,
        version: currentWf?.version,
        correlationId,
        input,
        taskToDomain,
        idempotencyKey,
        ...(idempotencyKey &&
          idempotencyStrategy && {
            idempotencyStrategy: idempotencyStrategy,
          }),
      };
      const postBody = JSON.stringify(postObject);
      const result = await fetchWithContext(
        "/workflow",
        {},
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...authHeaders,
          },
          body: postBody,
        },
        true,
      );

      // store history in local storage
      const existingHistory: [] =
        tryToJson(localStorage.getItem("workflowHistory")) || [];
      const newHistoryItem = {
        id: uuidv4(),
        executionLink: result,
        executionTime: Date.now(),
      };

      if (postObject) {
        Object.assign(newHistoryItem, postObject);
      }
      localStorage.setItem(
        "workflowHistory",
        JSON.stringify(
          [newHistoryItem, ...existingHistory].slice(0, RECORD_LIMIT),
        ),
      );

      return result;
    },
    customError: {
      message: GENERIC_ERROR_MESSAGE,
    },
    showCustomError: false,
  });
};
