import {
  ActorRef,
  assign,
  pure,
  send,
  sendParent,
  sendTo,
  spawn,
} from "xstate";
import {
  UpdateTaskEvent,
  TaskFormMachineContext,
  UpdateCrumbsEvent,
  SelectEdgeEvent,
} from "./types";
import { REPLACE_TASK_EVT } from "../../../state/constants";
import {
  TaskFormHeaderEventTypes,
  taskFormHeaderMachine,
  TaskHeaderMachineEvents,
} from "pages/definition/EditorPanel/TaskFormTab/forms/TaskFormHeader/state";
import { TaskType, TaskDef } from "types";
import { TaskStatsEventTypes } from "../TaskStats/state";
import _isNil from "lodash/isNil";
import fastDeepEqual from "fast-deep-equal";
import { FlowActionTypes } from "components/features/flow/state";
import _isUndefined from "lodash/isUndefined";
import _omitBy from "lodash/omitBy";

const maybeUseChanges = (
  defaultTo?: Partial<TaskDef>,
  maybeTask?: Partial<TaskDef>,
): Partial<TaskDef> | undefined => {
  if (
    _isNil(maybeTask) ||
    ![TaskType.SWITCH, TaskType.DO_WHILE].includes(maybeTask?.type as TaskType)
  ) {
    return defaultTo;
  }
  return fastDeepEqual(maybeTask, defaultTo) ? defaultTo : maybeTask;
};

export const spawnTaskHeaderMachineActor = assign<TaskFormMachineContext>(
  (context) => ({
    taskHeaderActor: spawn(
      taskFormHeaderMachine.withContext({
        name: context?.originalTask?.name || "",
        taskReferenceName: context?.originalTask?.taskReferenceName || "",
        taskType: context?.originalTask?.type || TaskType.SIMPLE, // TODO what if taskType is not set
      }),
      "taskFormHeader-fields",
    ),
  }),
);

export const updateTask = assign<TaskFormMachineContext, UpdateTaskEvent>({
  taskChanges: ({ taskChanges }, event) => {
    return _omitBy({ ...taskChanges, ...event.taskChanges }, _isUndefined);
  },
});

export const updateCollapseWorkflowList = sendParent<
  TaskFormMachineContext,
  any
>((_, { workflowName }) => ({
  type: FlowActionTypes.UPDATE_COLLAPSE_WORKFLOW_LIST,
  workflowName: workflowName,
}));

export const updateCrumbsAndOriginalTask = assign<
  TaskFormMachineContext,
  UpdateCrumbsEvent
>({
  crumbs: (_context, event) => {
    return event.crumbs;
  },
  originalTask: (context, event) =>
    maybeUseChanges(context.taskChanges, event?.task),
  taskChanges: (context, event) =>
    maybeUseChanges(context.taskChanges, event?.task),
});

export const maybePersistSelectedSwitchBranch = assign<
  TaskFormMachineContext,
  SelectEdgeEvent
>({
  maybeSelectedSwitchBranch: (context, { edge: { text } }) => text,
});

/* export const checkForErrors = sendParent<TaskFormMachineContext, any>( */
/*   ({ taskChanges }) => ({ */
/*     type: ErrorInspectorEventTypes.VALIDATE_SINGLE_TASK, */
/*     task: taskChanges, */
/*}) */
/* ); */

export const notifyChanges = sendParent(
  ({ originalTask, crumbs, taskChanges }: TaskFormMachineContext) => ({
    type: REPLACE_TASK_EVT,
    task: originalTask,
    crumbs,
    newTask: taskChanges,
  }),
);

export const notifyNameChange = send<TaskFormMachineContext, any>(
  ({ originalTask }) => ({
    type: TaskStatsEventTypes.UPDATE_TASK_NAME,
    name: originalTask?.name,
  }),
  { to: "taskStatsMachine" },
);

export const updateTaskHeaderMachine = pure<TaskFormMachineContext, any>(
  ({ originalTask }, { taskChanges }) => {
    if (taskChanges?.type !== originalTask?.type) {
      return sendTo<
        TaskFormMachineContext,
        any,
        ActorRef<TaskHeaderMachineEvents>
      >(
        "taskFormHeader-fields",
        ({ originalTask }, { taskChanges }) => {
          return {
            type: TaskFormHeaderEventTypes.VALUES_UPDATED,
            name: taskChanges?.name || "",
            taskReferenceName: taskChanges?.taskReferenceName || "",
            taskType:
              taskChanges?.type || originalTask?.type || TaskType.SIMPLE,
          };
        },
        { delay: 50 },
      );
    }
  },
);
