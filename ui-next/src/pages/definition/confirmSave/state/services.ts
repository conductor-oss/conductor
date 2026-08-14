import { removeCopyFromStorage } from "pages/definition/ConfirmLocalCopyDialog/state";
import { fetchWithContext } from "plugins/fetch";
import { WorkflowDef } from "types/WorkflowDef";
import { resolveAgentSnapshotsInWorkflow } from "utils/agentMetadata";
import { SaveWorkflowMachineContext } from "./types";

export { removeCopyFromStorage };

// Escape non-ASCII chars so the body is ASCII-safe before transit.
// Some WAF/proxy deployments misinterpret multibyte UTF-8 byte sequences
// (e.g. em-dash E2 80 94, whose third byte 0x94 = Windows-1252 right-quote)
// and silently corrupt the body, causing a Jackson "JSON parse error" on the server.
function asciiSafeJson(json: string): string {
  return json.replace(/[-￿]/g, (ch) =>
    `\\u${ch.charCodeAt(0).toString(16).padStart(4, "0")}`,
  );
}

export const resolveAgentSnapshots = async ({
  editorChanges,
  authHeaders,
}: SaveWorkflowMachineContext): Promise<string> => {
  const workflow = JSON.parse(editorChanges) as WorkflowDef;
  const resolved = await resolveAgentSnapshotsInWorkflow(
    workflow,
    (path, options) =>
      fetchWithContext(
        path,
        {},
        {
          method: options?.method ?? "GET",
          headers: {
            "Content-Type": "application/json",
            ...authHeaders,
          },
          body: options?.body,
        },
      ),
  );
  return JSON.stringify(resolved, null, 2);
};

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

        body: asciiSafeJson(editorChanges),
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
        body: `[${asciiSafeJson(editorChanges)}]`,
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
