import { fetchWithContext } from "plugins/fetch";
import { WorkflowMetadataMachineContext } from "./types";
import { getErrorMessage } from "utils/utils";
// const fetchContext = fetchContextNonHook();

export const createApplication = async (
  context: WorkflowMetadataMachineContext,
) => {
  const { authHeaders, metadataChanges } = context;
  try {
    return await fetchWithContext(
      "/applications",
      {},
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
        body: JSON.stringify({ name: metadataChanges.name }),
      },
    );
  } catch (error: any) {
    const errorMessage = await getErrorMessage(error);
    return {
      success: false,
      message: errorMessage ?? "Failed to create application",
    };
  }
};

export const updateApplication = async (
  context: WorkflowMetadataMachineContext,
) => {
  const { authHeaders } = context;
  const appCreateResponse = await createApplication(context);
  const { id } = appCreateResponse;

  const path = `/applications/${id}/roles/UNRESTRICTED_WORKER`;

  try {
    await fetchWithContext(
      path,
      {},
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
      },
    );
    return appCreateResponse;
  } catch (error: any) {
    const errorMessage = await getErrorMessage(error);
    return {
      success: false,
      message: errorMessage ?? "Failed to create application",
    };
  }
};

export const generateKeys = async (context: WorkflowMetadataMachineContext) => {
  const { authHeaders } = context;
  const genApp = await updateApplication(context);
  const { id } = genApp;
  const path = `/applications/${id}/accessKeys`;
  try {
    return await fetchWithContext(
      path,
      {},
      { method: "POST", headers: { ...authHeaders } },
    );
  } catch (error: any) {
    const errorMessage = await getErrorMessage(error);
    return {
      success: false,
      message: errorMessage ?? "Failed to generate keys",
    };
  }
};
