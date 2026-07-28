import { useCallback, useEffect, useRef } from "react";
import { AuthHeaders, TaskType } from "types/common";
import { DoWhileSelection, ExecutionTask } from "types/Execution";
import { featureFlags, FEATURES } from "utils/flags";
import {
  AugmentedExecutionTask,
  InlineTaskIterations,
} from "./InlineTaskIterations";
import { DoWhileIteration } from "./DoWhileIteration";
import { SummarizeConfirmDialog } from "./SummarizeConfirmDialog";
import { useSummarize } from "./useSummarize";
import { IterationPlaceholder } from "./iterationHelpers";

const summarizeEnabled = featureFlags.isEnabled(
  FEATURES.WORKFLOW_SUMMARIZE,
  true,
);

export interface IterationSectionProps {
  selectedTask: AugmentedExecutionTask;
  retryIterationOptions: (AugmentedExecutionTask | IterationPlaceholder)[];
  isIteration: boolean;
  handleSelectTask: (task: ExecutionTask) => void;
  handleSelectDoWhileIteration: (data: DoWhileSelection) => void;
  doWhileSelection?: DoWhileSelection[];
  executionId?: string;
  authHeaders?: AuthHeaders;
  parentDoWhileRef?: string;
}

/**
 * Renders the iteration list UI (InlineTaskIterations and/or DoWhileIteration)
 * for DO_WHILE-related tasks. Owns the summarize toggle state so it is shared
 * between both sub-components and persists across task navigation as long as
 * this component stays mounted.
 *
 * Only rendered by RightPanel when at least one of the two sub-components
 * would be visible, keeping summarize state off the critical path for
 * non-DO_WHILE tasks.
 */
export function IterationSection({
  selectedTask,
  retryIterationOptions,
  isIteration,
  handleSelectTask,
  handleSelectDoWhileIteration,
  doWhileSelection,
  executionId,
  authHeaders,
  parentDoWhileRef,
}: IterationSectionProps) {
  const {
    isSummarized,
    confirmOpen,
    handleToggleChange,
    handleConfirm,
    handleCancel,
  } = useSummarize();

  const isDoWhileContext =
    selectedTask.taskType === TaskType.DO_WHILE ||
    parentDoWhileRef != null ||
    isIteration;

  const toggleProps =
    summarizeEnabled && isDoWhileContext
      ? { isSummarized, onToggleSummarize: handleToggleChange }
      : { isSummarized: true, onToggleSummarize: undefined };

  // Remembers the last inline iteration the user explicitly picked so we can
  // restore it when they navigate away (e.g. to the DO_WHILE task) and back.
  const lastInlineSelection = useRef<{
    innerTaskRef: string;
    iteration: number;
  } | null>(null);

  const handleSelectTaskWithMemory = useCallback(
    (task: ExecutionTask) => {
      handleSelectTask(task);
      const innerRef = task.workflowTask?.taskReferenceName;
      if (task.iteration != null && innerRef) {
        lastInlineSelection.current = {
          innerTaskRef: innerRef,
          iteration: task.iteration,
        };
      }
    },
    [handleSelectTask],
  );

  // When selectedTask changes back to an inner task, restore the saved
  // iteration if it differs from what was clicked.
  useEffect(() => {
    const saved = lastInlineSelection.current;
    if (!saved) return;
    if (selectedTask?.taskType === TaskType.DO_WHILE) return;
    if (selectedTask?.workflowTask?.taskReferenceName !== saved.innerTaskRef)
      return;
    if (selectedTask?.iteration === saved.iteration) return;

    const match = retryIterationOptions?.find(
      (opt) => opt.iteration === saved.iteration && !("_placeholder" in opt),
    );
    if (match) handleSelectTask(match as ExecutionTask);
    // handleSelectTask is a stable dispatch — intentionally omitted from deps
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedTask?.taskId]);

  return (
    <>
      {summarizeEnabled && isDoWhileContext && (
        <SummarizeConfirmDialog
          open={confirmOpen}
          onCancel={handleCancel}
          onConfirm={handleConfirm}
        />
      )}
      {retryIterationOptions.length > 1 && (
        <InlineTaskIterations
          retryIterationOptions={retryIterationOptions}
          selectedTask={selectedTask}
          isIteration={isIteration}
          handleSelectTask={handleSelectTaskWithMemory}
          executionId={executionId}
          authHeaders={authHeaders}
          parentDoWhileRef={parentDoWhileRef}
          {...toggleProps}
        />
      )}
      {selectedTask.taskType === TaskType.DO_WHILE && (
        <DoWhileIteration
          selectedTask={selectedTask}
          doWhileSelection={doWhileSelection}
          handleSelectDoWhileIteration={handleSelectDoWhileIteration}
          handleSelectTask={handleSelectTask}
          executionId={executionId}
          authHeaders={authHeaders}
          {...toggleProps}
        />
      )}
    </>
  );
}
