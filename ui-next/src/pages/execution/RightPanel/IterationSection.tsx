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

  const toggleProps = summarizeEnabled
    ? { isSummarized, onToggleSummarize: handleToggleChange }
    : { isSummarized: true, onToggleSummarize: undefined };

  return (
    <>
      {summarizeEnabled && (
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
          handleSelectTask={handleSelectTask}
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
