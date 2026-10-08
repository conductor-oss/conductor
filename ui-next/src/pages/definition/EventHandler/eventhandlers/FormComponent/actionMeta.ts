import {
  CheckCircle,
  FlowArrow,
  Function as FunctionIcon,
  Icon,
  Prohibit,
  Robot,
  XCircle,
} from "@phosphor-icons/react";

import { EventHandlerAction } from "types/Events";
import {
  COMPLETE_TASK_ACTION,
  FAIL_TASK_ACTION,
  START_AGENT_ACTION,
  START_WORKFLOW_ACTION,
  TERMINATE_WORKFLOW_ACTION,
  UPDATE_VARIABLES_ACTION,
} from "../eventHandlerSchema";
import { Action } from "./state/types";

type ActionMeta = {
  label: string;
  description: string;
  icon: Icon;
  template: EventHandlerAction;
};

export const actionMeta: Record<Action, ActionMeta> = {
  [Action.COMPLETE_TASK]: {
    label: "Complete Task",
    description: "Mark a waiting task COMPLETED and pass it output.",
    icon: CheckCircle,
    template: COMPLETE_TASK_ACTION,
  },
  [Action.FAIL_TASK]: {
    label: "Fail Task",
    description: "Mark a waiting task FAILED with a reason.",
    icon: XCircle,
    template: FAIL_TASK_ACTION,
  },
  [Action.TERMINATE_WORKFLOW]: {
    label: "Terminate Workflow",
    description: "Stop a running workflow and record why.",
    icon: Prohibit,
    template: TERMINATE_WORKFLOW_ACTION,
  },
  [Action.UPDATE_WORKFLOW_VARIABLES]: {
    label: "Update Variables",
    description: "Set or merge variables on a running workflow.",
    icon: FunctionIcon,
    template: UPDATE_VARIABLES_ACTION,
  },
  [Action.START_WORKFLOW]: {
    label: "Start Workflow",
    description: "Start a new workflow execution with input.",
    icon: FlowArrow,
    template: START_WORKFLOW_ACTION,
  },
  [Action.START_AGENT]: {
    label: "Start Agent",
    description: "Run an agent with a prompt built from the event.",
    icon: Robot,
    template: START_AGENT_ACTION,
  },
};

export const actionOrder: Action[] = [
  Action.COMPLETE_TASK,
  Action.FAIL_TASK,
  Action.TERMINATE_WORKFLOW,
  Action.UPDATE_WORKFLOW_VARIABLES,
  Action.START_WORKFLOW,
  Action.START_AGENT,
];

const taskSummary = (task: { taskId?: string; taskRefName?: string }) =>
  task.taskId || task.taskRefName || "no task set";

/**
 * One-line preview shown in an action's header while it is collapsed. The
 * switch is exhaustive over the discriminant, so a new action variant is a
 * compile error here rather than a blank summary at runtime.
 */
export const actionSummary = (payload: EventHandlerAction): string => {
  switch (payload.action) {
    case "complete_task":
      return taskSummary(payload.complete_task);
    case "fail_task":
      return taskSummary(payload.fail_task);
    case "terminate_workflow":
      return payload.terminate_workflow.workflowId || "no workflow set";
    case "update_workflow_variables": {
      const keys = Object.keys(
        payload.update_workflow_variables.variables ?? {},
      );
      return keys.length ? keys.join(", ") : "no variables";
    }
    case "start_workflow": {
      const { name, version } = payload.start_workflow;
      if (!name) return "no workflow set";
      return version ? `${name} v${version}` : name;
    }
    case "start_agent":
      return payload.start_agent.name || "no agent set";
  }
};

/**
 * Templates are module-level constants shared by every action created from
 * them, so hand out a copy rather than letting edits write through.
 */
export const templateFor = (action: Action): EventHandlerAction =>
  structuredClone(actionMeta[action].template);
