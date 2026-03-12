import { DoneInvokeEvent, assign, sendParent } from "xstate";
import {
  ClearFormEvent,
  HandlePopoverMessageEvent,
  RunMachineContext,
  RunWorkflowParamType,
  UpdateAllFieldsEvent,
  UpdateCorrelationIdEvent,
  UpdateIdempotencyKeyEvent,
  UpdateInputParamsEvent,
  UpdateTasksToDomainEvent,
  UpdateIdempotencyStrategyEvent,
  UpdateIdempotencyValuesEvent,
} from "./types";
import { getTemplateFromInputParams } from "pages/runWorkflow/runWorkflowUtils";
import { WorkflowDef } from "types/WorkflowDef";
import { DefinitionMachineEventTypes } from "pages/definition/state";
import { tryToJson } from "utils/utils";
import _pick from "lodash/pick";
import _isEmpty from "lodash/isEmpty";
import { sendTo } from "xstate/lib/actions";
import { ErrorInspectorEventTypes } from "pages/definition/errorInspector/state";

const templateFromInputParams = (currentWf: Partial<WorkflowDef>) =>
  currentWf &&
  currentWf["inputParameters"] &&
  currentWf["inputParameters"].length > 0
    ? getTemplateFromInputParams(currentWf["inputParameters"])
    : "{}";

const shouldPreserveInput = (input?: string) =>
  input && input.trim() !== "" && input.trim() !== "{}";

const getInputFromHistory = (currentWf: any) => {
  const history: RunWorkflowParamType[] =
    tryToJson(localStorage.getItem("workflowHistory")) || [];
  const filtered = history.filter((item) => item.name === currentWf.name);
  if (filtered.length > 0) {
    const historyInput = filtered[0].input ?? {};
    const wfInput =
      tryToJson(getTemplateFromInputParams(currentWf["inputParameters"])) || {};
    let merged = { ...wfInput, ...historyInput };
    merged = _pick(merged, currentWf.inputParameters ?? []);
    return JSON.stringify(merged, null, 2);
  }
  return null;
};

export const persistInputParams = assign<
  RunMachineContext,
  UpdateInputParamsEvent
>({
  input: (_context, { changes }) => changes,
});

export const persistCorrelationId = assign<
  RunMachineContext,
  UpdateCorrelationIdEvent
>({
  correlationId: (_context, { changes }) => changes,
});

export const persistIdempotencyKey = assign<
  RunMachineContext,
  UpdateIdempotencyKeyEvent
>({
  idempotencyKey: (_context, { changes }) => changes,
});

export const persistIdempotencyStrategy = assign<
  RunMachineContext,
  UpdateIdempotencyStrategyEvent
>({
  idempotencyStrategy: (_context, { changes }) => changes,
});

export const persistIdempotencyValues = assign<
  RunMachineContext,
  UpdateIdempotencyValuesEvent
>({
  idempotencyKey: (_context, { changes }) => changes?.idempotencyKey,
  idempotencyStrategy: (_context, { changes }) => changes?.idempotencyStrategy,
});

export const persistTasksToDomain = assign<
  RunMachineContext,
  UpdateTasksToDomainEvent
>({
  taskToDomain: (_context, { changes }) => changes,
});
export const clearForm = assign<RunMachineContext, ClearFormEvent>(
  (context, _data) => {
    return {
      taskToDomain: "{}",
      correlationId: "",
      input: templateFromInputParams(context.currentWf ?? {}),
      idempotencyKey: "",
    };
  },
);

export const checkForExistingInputParams = assign<
  RunMachineContext,
  DoneInvokeEvent<any>
>((context) => {
  if (shouldPreserveInput(context.input)) {
    return {};
  }

  let input = getInputFromHistory(context.currentWf);

  // If no history, check for default parameters first
  if (
    (!input || input.trim() === "{}") &&
    !_isEmpty(context.workflowDefaultRunParam)
  ) {
    input = JSON.stringify(context.workflowDefaultRunParam, null, 2);
  }
  // Only fall back to empty template if no history and no defaults
  if (!input) {
    input = templateFromInputParams(context.currentWf ?? {});
  }

  return {
    input,
  };
});

export const persistPopupMessage = assign<
  RunMachineContext,
  HandlePopoverMessageEvent
>((_, { popoverMessage }) => ({
  popoverMessage,
}));

export const redirectToNewExecution = sendParent(
  (context: RunMachineContext, { data }: { type: string; data: string }) => ({
    type: DefinitionMachineEventTypes.REDIRECT_TO_EXECUTION_PAGE,
    executionId: data,
  }),
);

export const persistAllFields = assign<RunMachineContext, UpdateAllFieldsEvent>(
  (_context, { data }) => data,
);

export const reportErrorToErrorInspector = sendTo(
  ({ errorInspectorMachine }) => errorInspectorMachine,
  (_context, { data }: DoneInvokeEvent<{ message: string }>) => ({
    type: ErrorInspectorEventTypes.REPORT_RUN_ERROR,
    text: data.message,
  }),
);

export const sendContextToParent = sendParent(
  (context: RunMachineContext, event) => ({
    type: DefinitionMachineEventTypes.SYNC_RUN_CONTEXT_AND_CHANGE_TAB,
    data: {
      originalEvent: event,
      runMachineContext: {
        input: context.input,
        correlationId: context.correlationId,
        taskToDomain: context.taskToDomain,
        idempotencyKey: context.idempotencyKey,
        idempotencyStrategy: context.idempotencyStrategy,
      },
    },
  }),
);
