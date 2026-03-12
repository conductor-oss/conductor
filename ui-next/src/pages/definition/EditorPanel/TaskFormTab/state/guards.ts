import fastDeepEqual from "fast-deep-equal";
import {
  TaskFormMachineContext,
  UpdateTaskEvent,
} from "pages/definition/EditorPanel/TaskFormTab/state/types";

export const isTaskChanged = (
  context: TaskFormMachineContext,
  event: UpdateTaskEvent,
) => !fastDeepEqual(context.taskChanges, event.taskChanges);
