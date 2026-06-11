import { Box, Typography } from "@mui/material";
import { fetchExecutionFull } from "commonServices";
import ConductorTooltip from "components/ui/ConductorTooltip";
import _nth from "lodash/nth";
import { useCallback, useMemo, useState } from "react";
import { useQuery } from "react-query";
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
import { IterationStatusIcon } from "./IterationStatusIcon";
import { SummarizeConfirmDialog } from "./SummarizeConfirmDialog";
import { SummarizeToggle } from "./SummarizeToggle";

export interface DoWhileIterationProps {
  selectedTask: ExecutionTask;
  handleSelectDoWhileIteration: (data: DoWhileSelection) => void;
  doWhileSelection?: DoWhileSelection[];
  executionId?: string;
  authHeaders?: AuthHeaders;
}

export const DoWhileIteration = ({
  selectedTask,
  handleSelectDoWhileIteration,
  doWhileSelection,
  executionId,
  authHeaders,
}: DoWhileIterationProps) => {
  const [isSummarized, setIsSummarized] = useState(true);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const taskReferenceName = selectedTask?.referenceTaskName;

  // Shared query with InlineTaskIterations — one fetch, cached for both.
  const { data: fullWorkflow } = useQuery(
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

  const headerLabel = (
    <Box
      component="span"
      sx={{ display: "inline-flex", alignItems: "center", gap: 0.75 }}
    >
      <IterationStatusIcon status={selectedTask.status} size={13} />
      <span>{headerText}</span>
    </Box>
  );

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

  const trailing = (
    <>
      {keepLastNTrailing}
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
    </>
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
        items={iterationOptions}
        headerLabel={headerLabel}
        trailing={trailing}
        totalItems={iterationOptions.length}
        getOptionLabel={(option) => `Iteration ${option}`}
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
