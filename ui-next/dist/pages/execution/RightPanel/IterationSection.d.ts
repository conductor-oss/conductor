import { AuthHeaders } from "types/common";
import { DoWhileSelection, ExecutionTask } from "types/Execution";
import { AugmentedExecutionTask } from "./InlineTaskIterations";
import { IterationPlaceholder } from "./iterationHelpers";
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
export declare function IterationSection({ selectedTask, retryIterationOptions, isIteration, handleSelectTask, handleSelectDoWhileIteration, doWhileSelection, executionId, authHeaders, parentDoWhileRef, }: IterationSectionProps): import("react").JSX.Element;
