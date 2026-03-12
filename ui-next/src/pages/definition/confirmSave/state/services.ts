import { removeCopyFromStorage } from "pages/definition/ConfirmLocalCopyDialog/state";
import { fetchWithContext } from "plugins/fetch";
import { WorkflowDef } from "types/WorkflowDef";
import { SaveWorkflowMachineContext } from "./types";

export { removeCopyFromStorage };

export const createWorkflow = async (
  { editorChanges, authHeaders }: SaveWorkflowMachineContext,
  __: any,
) => {
  try {
    return await fetchWithContext(
      "/metadata/workflow",
      {},
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },

        body: editorChanges,
      },
    );
  } catch (error: any) {
    const errorBody = await error.json();
    return Promise.reject({
      text: errorBody.message,
      severity: "error",
      status: errorBody.status,
      validationErrors: errorBody?.validationErrors,
    });
  }
};

export const updateWorkflow = async (
  { editorChanges, authHeaders, isNewVersion }: SaveWorkflowMachineContext,
  __: any,
) => {
  const queryParams = isNewVersion ? "?newVersion=true" : "";
  try {
    return await fetchWithContext(
      `/metadata/workflow${queryParams}`,
      {},
      {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
        body: `[${editorChanges}]`,
      },
    );
  } catch (error: any) {
    const errorBody = await error.json();
    return Promise.reject({
      text: errorBody.message,
      severity: "error",
      status: errorBody.status,
      validationErrors: errorBody?.validationErrors,
    });
  }
};

export const refetchAllDefinitionsOfCurrentWorkflow = async ({
  authHeaders: headers,
  workflowName,
}: SaveWorkflowMachineContext) => {
  const url = `/metadata/workflow?name=${encodeURIComponent(workflowName)}`;
  try {
    const result: WorkflowDef[] = await fetchWithContext(
      url,
      {},
      {
        method: "GET",
        headers: {
          "Content-Type": "application/json",
          ...headers,
        },
      },
    );
    return result;
  } catch {
    return {};
  }
};
