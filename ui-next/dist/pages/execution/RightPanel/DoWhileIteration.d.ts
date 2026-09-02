import { AuthHeaders } from "types/common";
import { DoWhileSelection, ExecutionTask } from "types/Execution";
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
export declare const DoWhileIteration: ({ selectedTask, handleSelectDoWhileIteration, handleSelectTask, doWhileSelection, executionId, authHeaders, isSummarized, onToggleSummarize, }: DoWhileIterationProps) => import("react").JSX.Element;
