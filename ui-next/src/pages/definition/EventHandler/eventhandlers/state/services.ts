import _isEmpty from "lodash/isEmpty";
import { fetchWithContext } from "plugins/fetch";
import { SaveEventHandlerMachineContext } from "./types";
import { queryClient } from "../../../../../queryClient";
import { fetchContextNonHook } from "plugins/fetch";
import { getErrors, logger, tryFunc } from "utils";
import { NEW_EVENT_HANDLER_TEMPLATE } from "../eventHandlerSchema";

const fetchContext = fetchContextNonHook();

export const createEventHandler = async (
  { editorChanges, authHeaders }: SaveEventHandlerMachineContext,
  __: any,
) => {
  return tryFunc({
    fn: async () => {
      return await fetchWithContext(
        "/event",
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
    },
  });
};

export const updateEventHandler = async (
  { editorChanges, authHeaders }: SaveEventHandlerMachineContext,
  __: any,
) => {
  return tryFunc({
    fn: async () => {
      return await fetchWithContext(
        "/event",
        {},
        {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
            ...authHeaders,
          },
          body: editorChanges,
        },
      );
    },
  });
};

export const fetchEventHandler = async (
  {
    authHeaders,
    eventHandlerName,
    isNewEventHandler,
  }: SaveEventHandlerMachineContext,
  __: any,
) => {
  // OSS Conductor doesn't have a /event/handler/{name} endpoint
  // We need to fetch all event handlers and filter by name
  const path = "/event";

  return tryFunc({
    fn: async () => {
      if (isNewEventHandler) {
        return NEW_EVENT_HANDLER_TEMPLATE;
      }

      const allHandlers = await queryClient.fetchQuery(
        [path, eventHandlerName],
        () => fetchWithContext(path, fetchContext, { headers: authHeaders }),
      );

      // Find the event handler by name
      const result = Array.isArray(allHandlers)
        ? allHandlers.find((handler: any) => handler.name === eventHandlerName)
        : null;

      if (_isEmpty(result)) {
        return Promise.reject({ message: "Event handler not found" });
      }

      return result;
    },
    customError: { message: "Event handler not found" },
  });
};

export const deleteEventHandler = async (
  { eventHandlerName, authHeaders }: SaveEventHandlerMachineContext,
  __: any,
) => {
  try {
    const path = `/event/${encodeURIComponent(eventHandlerName)}`;
    return await fetchWithContext(path, fetchContext, {
      method: "DELETE",
      headers: authHeaders,
    });
  } catch (error: any) {
    logger.error("[deleteEventHandler] error:", error);
    if (error?.status === 403) {
      return Promise.reject({
        message: "You do not have permission to delete this event handler.",
      });
    }
    const details = await getErrors(error);
    return Promise.reject({
      originalError: details,
      message: details?.message,
    });
  }
};
