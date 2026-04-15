import { fetchWithContext } from "plugins/fetch";
import { CreateAndDisplayApplicationMachineContext } from "./types";
import { getErrorMessage } from "utils/utils";
import { AccessRole, User } from "types/User";
// const fetchContext = fetchContextNonHook();

export const createApplication = async (
  context: CreateAndDisplayApplicationMachineContext,
) => {
  const { authHeaders, applicationName } = context;
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
        body: JSON.stringify({ name: applicationName }),
      },
    );
  } catch (error: any) {
    const errorMessage = await getErrorMessage(error);
    throw new Error(errorMessage ?? "Failed to create application");
  }
};

export const fetchForAppDetails = async (
  context: CreateAndDisplayApplicationMachineContext,
): Promise<User> => {
  const { authHeaders, applicationId } = context;
  const path = `/users/app:${applicationId}`;

  const appDetails = await fetchWithContext(
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
  return appDetails;
};

export const checkIfAppExistsAndCompatible = async (
  context: CreateAndDisplayApplicationMachineContext,
) => {
  const { authHeaders, applicationName } = context;
  try {
    const appList = await fetchWithContext(
      "/applications",
      {},
      {
        method: "GET",
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
        },
      },
    );

    const app = appList.find((app: any) => app.name === applicationName);

    if (app) {
      const appUserDetails = await fetchForAppDetails({
        ...context,
        applicationId: app.id,
      });
      if (
        appUserDetails.roles.find(
          (role: AccessRole) => role.name === "UNRESTRICTED_WORKER",
        )
      ) {
        return { id: app.id };
      }
    }

    return { id: null };
  } catch (error: any) {
    const errorMessage = await getErrorMessage(error);
    throw new Error(errorMessage ?? "Failed to create application");
  }
};

export const createApplicationWithRoles = async (
  context: CreateAndDisplayApplicationMachineContext,
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
    throw new Error(errorMessage ?? "Failed to create application");
  }
};

export const generateKeys = async (
  context: CreateAndDisplayApplicationMachineContext,
) => {
  const { authHeaders, applicationId } = context;
  const path = `/applications/${applicationId}/accessKeys`;
  try {
    return await fetchWithContext(
      path,
      {},
      { method: "POST", headers: { ...authHeaders } },
    );
  } catch {
    throw new Error("Failed to generate keys");
  }
};
