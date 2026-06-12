import { Box, Typography } from "@mui/material";
import ConductorTooltip from "components/ui/ConductorTooltip";
import _nth from "lodash/nth";
import { useCallback, useEffect, useMemo } from "react";
import { colors } from "theme/tokens/variables";
import { AuthHeaders } from "types/common";
import { DoWhileSelection, ExecutionTask } from "types/Execution";
import { TaskStatus } from "types/TaskStatus";
import { CollapsibleIterationList } from "./CollapsibleIterationList";
import {
  deriveFallbackIterationStatus,
  getOrderedIterationKeys,
  isIterationSummarized,
} from "./doWhileIterationHelpers";
import { IterationHeaderLabel } from "./IterationHeaderLabel";
import { IterationStatusIcon } from "./IterationStatusIcon";
import { SummarizeToggle } from "./SummarizeToggle";
import { useFullWorkflowQuery } from "./useFullWorkflowQuery";

interface AugmentedDoWhileTask extends ExecutionTask {
  _summarized?: boolean;
}

export interface DoWhileIterationProps {
  selectedTask: ExecutionTask;
  handleSelectDoWhileIteration: (data: DoWhileSelection) => void;
  handleSelectTask?: (task: ExecutionTask) => void;
  doWhileSelection?: DoWhileSelection[];
  executionId?: string;
  authHeaders?: AuthHeaders;
  isSummarized: boolean;
  onToggleSummarize?: (checked: boolean) => void;
}

export const DoWhileIteration = ({
  selectedTask,
  handleSelectDoWhileIteration,
  handleSelectTask,
  doWhileSelection,
  executionId,
  authHeaders,
  isSummarized,
  onToggleSummarize,
}: DoWhileIterationProps) => {
  const taskReferenceName = selectedTask?.referenceTaskName;

  // Shared query with InlineTaskIterations — one fetch, cached for both.
  const { data: fullWorkflow } = useFullWorkflowQuery(
    executionId,
    authHeaders,
    !isSummarized,
  );

  // When full data is available use the DO_WHILE task's real outputData
  // (without summarize sentinels). Fall back to the locally-loaded task.
  const fullDoWhileTask = useMemo(
    () =>
      fullWorkflow?.tasks?.find(
        (t: ExecutionTask) =>
          t.taskType === "DO_WHILE" &&
          (t.referenceTaskName === taskReferenceName ||
            t.workflowTask?.taskReferenceName === taskReferenceName),
      ),
    [fullWorkflow, taskReferenceName],
  );

  const outputData = useMemo(
    () =>
      (!isSummarized && fullDoWhileTask?.outputData) ||
      selectedTask?.outputData ||
      {},
    [isSummarized, fullDoWhileTask, selectedTask],
  );

  const iterationOptions = useMemo(
    () => getOrderedIterationKeys(outputData, selectedTask),
    [outputData, selectedTask],
  );

  // When the user toggles off summarize, re-select the DO_WHILE task with its
  // full version so Input/Output tabs reflect the newly loaded data.
  useEffect(() => {
    if (isSummarized || !fullDoWhileTask || !handleSelectTask) return;
    const isCurrentlySummarized =
      (selectedTask as AugmentedDoWhileTask)._summarized === true;
    if (isCurrentlySummarized) {
      handleSelectTask(fullDoWhileTask);
    }
    // handleSelectTask is intentionally omitted — it's a stable actor dispatch
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isSummarized, fullDoWhileTask, selectedTask]);

  const isTaskProcessing = [
    TaskStatus.PENDING,
    TaskStatus.SCHEDULED,
    TaskStatus.IN_PROGRESS,
  ].includes(selectedTask.status);

  const currentIteration = _nth(
    doWhileSelection?.filter(
      (item) =>
        item.doWhileTaskReferenceName === selectedTask?.referenceTaskName,
    ),
    0,
  )?.selectedIteration;

  const handleSelect = useCallback(
    (option: number) => {
      handleSelectDoWhileIteration({
        doWhileTaskReferenceName: selectedTask?.referenceTaskName,
        selectedIteration: option,
      });
    },
    [handleSelectDoWhileIteration, selectedTask?.referenceTaskName],
  );

  const headerText =
    currentIteration != null
      ? `Iteration ${currentIteration}`
      : `Iterations (${iterationOptions.length})`;

  const keepLastNTrailing =
    selectedTask?.inputData?.keepLastN != null ? (
      <ConductorTooltip
        title=""
        content={`keepLastN is set to ${selectedTask?.inputData?.keepLastN}`}
        placement="top"
      >
        <img
          alt="info"
          src="/icons/info-icon.svg"
          style={{ paddingLeft: "3px" }}
        />
      </ConductorTooltip>
    ) : null;

  const trailing =
    keepLastNTrailing || onToggleSummarize ? (
      <>
        {keepLastNTrailing}
        {onToggleSummarize && (
          <SummarizeToggle
            checked={isSummarized}
            onChange={onToggleSummarize}
          />
        )}
      </>
    ) : undefined;

  return (
    <>
      <CollapsibleIterationList
        items={iterationOptions}
        headerLabel={
          <IterationHeaderLabel
            status={selectedTask.status}
            text={headerText}
          />
        }
        trailing={trailing}
        totalItems={iterationOptions.length}
        getItemValue={(option) => option}
        onJumpTo={handleSelect}
        onSelect={(option) => handleSelect(option)}
        isItemSelected={(option) => currentIteration === option}
        renderItem={(option) => {
          const summarized = isIterationSummarized(
            option,
            outputData,
            isTaskProcessing,
          );
          return (
            <>
              <Box
                component="span"
                sx={{ minWidth: 18, display: "flex", alignItems: "center" }}
              >
                <IterationStatusIcon
                  status={deriveFallbackIterationStatus(
                    option,
                    outputData,
                    selectedTask.status,
                  )}
                  size={13}
                />
              </Box>
              <Box component="span">Iteration {option}</Box>
              {summarized && (
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
