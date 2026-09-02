import { AuthHeaders } from "types/common";
import { ExecutionTask } from "types/Execution";
import { IterationPlaceholder } from "./iterationHelpers";
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
export declare const InlineTaskIterations: ({ retryIterationOptions, selectedTask, isIteration, handleSelectTask, executionId, authHeaders, parentDoWhileRef, isSummarized, onToggleSummarize, }: InlineTaskIterationsProps) => import("react").JSX.Element;
