import { getErrors } from "utils";
import {
  FORBIDDEN_CREATE_SCHEDULE_ERROR_MESSAGE,
  FORBIDDEN_UPDATE_SCHEDULE_ERROR_MESSAGE,
} from "utils/constants/common";

export type ScheduleMutationAction = "create" | "update" | "clone";

export async function getScheduleMutationErrorMessage(
  response: Response,
  action: ScheduleMutationAction,
): Promise<string> {
  if (response.status === 403) {
    const actionLabel =
      action === "clone"
        ? "Clone failed."
        : action === "create"
          ? "Save failed."
          : "Save failed.";
    const permissionMessage =
      action === "update"
        ? FORBIDDEN_UPDATE_SCHEDULE_ERROR_MESSAGE
        : FORBIDDEN_CREATE_SCHEDULE_ERROR_MESSAGE;
    return `${actionLabel} ${permissionMessage}`;
  }

  const errors = await getErrors(response);
  if (errors.message) {
    return errors.message;
  }

  return `Something went wrong (${response.status} ${response.statusText})`;
}
