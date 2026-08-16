import { Box, Typography } from "@mui/material";
import { useEffect, useMemo } from "react";
import { colors } from "theme/tokens/variables";
import { AuthHeaders } from "types/common";
import { ExecutionTask } from "types/Execution";
import { TaskStatus } from "types/TaskStatus";
import { CollapsibleIterationList } from "./CollapsibleIterationList";
import { IterationHeaderLabel } from "./IterationHeaderLabel";
import {
  fillIterationPlaceholders,
  IterationPlaceholder,
} from "./iterationHelpers";
import { IterationStatusIcon } from "./IterationStatusIcon";
import { SummarizeToggle } from "./SummarizeToggle";
import { useFullWorkflowQuery } from "./useFullWorkflowQuery";

/**
 * ExecutionTask augmented with UI-internal fields injected by hook.ts when
 * building synthetic placeholder rows for iterations not yet in the task list.
 */
export interface AugmentedExecutionTask extends ExecutionTask {
  iteration?: number;
  _parentDoWhileRef?: string;
  _summarized?: boolean;
  _totalIterations?: number;
}

export interface InlineTaskIterationsProps {
  retryIterationOptions: (AugmentedExecutionTask | IterationPlaceholder)[];
  selectedTask: AugmentedExecutionTask;
  isIteration: boolean;
  handleSelectTask: (task: ExecutionTask) => void;
  executionId?: string;
  authHeaders?: AuthHeaders;
  parentDoWhileRef?: string;
  isSummarized: boolean;
  onToggleSummarize?: (checked: boolean) => void;
}

export const InlineTaskIterations = ({
  retryIterationOptions,
  selectedTask,
  isIteration,
  handleSelectTask,
  executionId,
  authHeaders,
  parentDoWhileRef,
  isSummarized,
  onToggleSummarize,
}: InlineTaskIterationsProps) => {
  const innerTaskRef = selectedTask?.workflowTask?.taskReferenceName;

  // Shared query with DoWhileIteration — one fetch, cached for both.
  const { data: fullWorkflow, isFetching } = useFullWorkflowQuery(
    executionId,
    authHeaders,
    !isSummarized,
  );

  // When full data is available, rebuild the iteration list from the complete
  // task list rather than the summarized loopOver. This resolves placeholder
  // items that were created because the initial load used summarize=true.
  const resolvedOptions = useMemo(() => {
    if (isSummarized || !fullWorkflow?.tasks) return retryIterationOptions;

    const innerTasks: AugmentedExecutionTask[] = fullWorkflow.tasks.filter(
      (t: ExecutionTask) =>
        t.workflowTask?.taskReferenceName === innerTaskRef &&
        (t.iteration ?? 0) > 0,
    );

    // totalIterations is preserved from the original (possibly summarized) list
    // since retryIterationOptions always has length == total iteration count.
    const totalIterations = retryIterationOptions.length;

    return fillIterationPlaceholders(
      innerTasks,
      totalIterations,
      parentDoWhileRef,
      selectedTask.workflowTask,
    );
  }, [
    isSummarized,
    fullWorkflow,
    retryIterationOptions,
    innerTaskRef,
    parentDoWhileRef,
    selectedTask.workflowTask,
  ]);

  // When the user toggles off summarize, re-select the current task with its
  // full version so Input/Output tabs reflect the newly loaded data.
  useEffect(() => {
    if (isSummarized || !fullWorkflow || !selectedTask._summarized) return;
    const fullTask = resolvedOptions.find(
      (opt) => opt.iteration === selectedTask.iteration,
    );
    if (fullTask && !("_placeholder" in fullTask)) {
      handleSelectTask(fullTask as ExecutionTask);
    }
    // handleSelectTask is intentionally omitted — it's a stable actor dispatch.
    // selectedTask.taskId is included so the effect re-fires when the selected
    // task changes to a different summarized task (e.g. via iteration restore).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isSummarized, fullWorkflow, resolvedOptions, selectedTask.taskId]);

  const effectiveIteration = selectedTask.iteration;
  const hasKnownIteration =
    isIteration ||
    (typeof effectiveIteration === "number" && effectiveIteration > 0);

  const headerText = hasKnownIteration
    ? `Iteration ${effectiveIteration ?? ""}`
    : `Attempt #${selectedTask.retryCount ?? 0}`;

  const getItemIndex = (task: AugmentedExecutionTask): number =>
    typeof task.iteration === "number" && task.iteration > 0
      ? task.iteration
      : (task.retryCount ?? 0);

  const getItemLabel = (task: AugmentedExecutionTask): string =>
    typeof task.iteration === "number" && task.iteration > 0
      ? `Iteration ${task.iteration}`
      : `Attempt #${task.retryCount ?? 0}`;

  return (
    <>
      <CollapsibleIterationList
        items={resolvedOptions}
        headerLabel={
          <IterationHeaderLabel
            status={selectedTask.status as TaskStatus}
            text={headerText}
          />
        }
        trailing={
          onToggleSummarize ? (
            <SummarizeToggle
              checked={isSummarized}
              onChange={onToggleSummarize}
            />
          ) : undefined
        }
        totalItems={resolvedOptions.length}
        getItemValue={(item) => getItemIndex(item as AugmentedExecutionTask)}
        onJumpTo={(value) => {
          const match = resolvedOptions.find(
            (opt) => getItemIndex(opt as AugmentedExecutionTask) === value,
          );
          if (match) handleSelectTask(match as ExecutionTask);
        }}
        onSelect={(option) => handleSelectTask(option as ExecutionTask)}
        isItemSelected={(option) =>
          getItemIndex(option as AugmentedExecutionTask) ===
          getItemIndex(selectedTask)
        }
        renderItem={(option) => {
          const task = option as AugmentedExecutionTask;
          const showSummarized = task._summarized === true;
          const isLoading = isFetching && task._summarized === true;
          return (
            <>
              <Box
                component="span"
                sx={{ minWidth: 18, display: "flex", alignItems: "center" }}
              >
                <IterationStatusIcon
                  status={(task.status as TaskStatus) ?? TaskStatus.COMPLETED}
                  size={13}
                />
              </Box>
              <Box component="span">{getItemLabel(task)}</Box>
              {!showSummarized && task.taskId && (
                <Typography
                  component="span"
                  sx={{
                    fontSize: "8pt",
                    color: colors.gray04,
                    ml: 1,
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                    minWidth: 0,
                  }}
                >
                  {task.taskId}
                </Typography>
              )}
              {isLoading && (
                <Typography
                  component="span"
                  sx={{ fontSize: "8pt", color: colors.gray04, ml: 1 }}
                >
                  loading…
                </Typography>
              )}
              {!isLoading && showSummarized && (
                <Typography
                  component="span"
                  sx={{
                    fontSize: "8pt",
                    color: colors.gray04,
                    opacity: 0.7,
                    ml: 1,
                  }}
                >
                  (summarized)
                </Typography>
              )}
            </>
          );
        }}
      />
    </>
  );
};
