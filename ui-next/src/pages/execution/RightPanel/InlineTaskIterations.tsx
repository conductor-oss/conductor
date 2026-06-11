import { Box, Typography } from "@mui/material";
import { fetchExecutionFull } from "commonServices";
import { useMemo, useState } from "react";
import { useQuery } from "react-query";
import { colors } from "theme/tokens/variables";
import { AuthHeaders } from "types/common";
import { ExecutionTask } from "types/Execution";
import { TaskStatus } from "types/TaskStatus";
import { CollapsibleIterationList } from "./CollapsibleIterationList";
import { IterationStatusIcon } from "./IterationStatusIcon";
import {
  fillIterationPlaceholders,
  IterationPlaceholder,
} from "./iterationHelpers";
import { SummarizeConfirmDialog } from "./SummarizeConfirmDialog";
import { SummarizeToggle } from "./SummarizeToggle";

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
}

export const InlineTaskIterations = ({
  retryIterationOptions,
  selectedTask,
  isIteration,
  handleSelectTask,
  executionId,
  authHeaders,
  parentDoWhileRef,
}: InlineTaskIterationsProps) => {
  const [isSummarized, setIsSummarized] = useState(true);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const innerTaskRef = selectedTask?.workflowTask?.taskReferenceName;

  // Shared query with DoWhileIteration — one fetch, cached for both.
  const { data: fullWorkflow, isFetching } = useQuery(
    ["workflow-full", executionId],
    () =>
      fetchExecutionFull({
        authHeaders: authHeaders as any,
        executionId: executionId!,
      }),
    {
      enabled: !isSummarized && !!executionId,
      staleTime: Infinity,
    },
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

  const effectiveIteration = selectedTask.iteration;
  const hasKnownIteration =
    isIteration ||
    (typeof effectiveIteration === "number" && effectiveIteration > 0);

  const headerText = hasKnownIteration
    ? `Iteration ${effectiveIteration ?? ""}`
    : `Attempt #${selectedTask.retryCount ?? 0}`;

  const headerLabel = (
    <Box
      component="span"
      sx={{ display: "inline-flex", alignItems: "center", gap: 0.75 }}
    >
      <IterationStatusIcon
        status={selectedTask.status as TaskStatus}
        size={13}
      />
      <span>{headerText}</span>
    </Box>
  );

  return (
    <>
      <SummarizeConfirmDialog
        open={confirmOpen}
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => {
          setIsSummarized(false);
          setConfirmOpen(false);
        }}
      />

      <CollapsibleIterationList
        items={resolvedOptions}
        headerLabel={headerLabel}
        trailing={
          <SummarizeToggle
            checked={isSummarized}
            onChange={(checked) => {
              if (checked) {
                setIsSummarized(true);
              } else {
                setConfirmOpen(true);
              }
            }}
          />
        }
        totalItems={resolvedOptions.length}
        getOptionLabel={(item) => `Iteration ${item.iteration ?? ""}`}
        getItemValue={(item) => item.iteration ?? 0}
        onJumpTo={(iterationNum) => {
          const match = resolvedOptions.find(
            (opt) => opt.iteration === iterationNum,
          );
          if (match) handleSelectTask(match as ExecutionTask);
        }}
        onSelect={(option) => handleSelectTask(option as ExecutionTask)}
        isItemSelected={(option) => option.iteration === selectedTask.iteration}
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
              <Box component="span">Iteration {task.iteration}</Box>
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
