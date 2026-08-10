import { logger } from "utils";
import { SaveEventHandlerMachineContext } from "./types";

const maybeEventHandlerName = (eventHandlerAsString: string): string | null => {
  try {
    const eventHandler = JSON.parse(eventHandlerAsString);
    const { name = null } = eventHandler;
    return name;
  } catch {
    logger.debug("Event handler editor changes is not parsable");
  }
  return null;
};

export const isNewOrNameChanged = ({
  isNewEventHandler,
  originalSource,
  editorChanges,
}: SaveEventHandlerMachineContext) => {
  return (
    isNewEventHandler ||
    maybeEventHandlerName(editorChanges) !==
      maybeEventHandlerName(originalSource)
  );
};
