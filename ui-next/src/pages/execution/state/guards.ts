import isNil from "lodash/isNil";
import {
  COUNT_DOWN_TYPE,
  ExecutionMachineContext,
  ExecutionTabs,
} from "./types";
import { HttpStatusCode } from "utils/constants/httpStatusCode";
import { DoneInvokeEvent } from "xstate";

const WOKFLOW_TERMINATED_STATUS = [
  "COMPLETED",
  "FAILED",
  "TIMED_OUT",
  "TERMINATED",
];

export const canWorkflowChangeState = (context: ExecutionMachineContext) =>
  !WOKFLOW_TERMINATED_STATUS.includes(context?.execution?.status || "") &&
  !isNil(context.execution);

export const isExecutionTerminated = (context: ExecutionMachineContext) =>
  context?.execution?.status === "TERMINATED";
export const isExecutionCompleted = (context: ExecutionMachineContext) =>
  context?.execution?.status === "COMPLETED";
export const isExecutionFailed = (context: ExecutionMachineContext) =>
  context?.execution?.status === "FAILED";
export const isExecutionTimedOut = (context: ExecutionMachineContext) =>
  context?.execution?.status === "TIMED_OUT";
export const isExecutionPaused = (context: ExecutionMachineContext) =>
  context?.execution?.status === "PAUSED";

export const isTaskListTab = ({ currentTab }: ExecutionMachineContext) =>
  currentTab === ExecutionTabs.TASK_LIST_TAB;

export const isTimeLineTab = ({ currentTab }: ExecutionMachineContext) =>
  currentTab === ExecutionTabs.TIMELINE_TAB;

export const isTimeWorkflowInputOutputTab = ({
  currentTab,
}: ExecutionMachineContext) =>
  currentTab === ExecutionTabs.WORKFLOW_INPUT_OUTPUT_TAB;

export const isJsonTab = ({ currentTab }: ExecutionMachineContext) =>
  currentTab === ExecutionTabs.JSON_TAB;

export const isSummaryTab = ({ currentTab }: ExecutionMachineContext) =>
  currentTab === ExecutionTabs.SUMMARY_TAB;

export const isInfinityCountdown = (context: ExecutionMachineContext) =>
  context?.countdownType === COUNT_DOWN_TYPE.INFINITE;

export const isUseGlobalMessage = (
  __: ExecutionMachineContext,
  event: DoneInvokeEvent<{
    originalError: Response;
    errorDetails: { message: string };
  }>,
) => event?.data?.originalError?.status === HttpStatusCode.Forbidden;
