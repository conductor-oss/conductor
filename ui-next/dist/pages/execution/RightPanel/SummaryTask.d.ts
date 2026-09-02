import { FunctionComponent } from "react";
import { ExecutionTask } from "types";
interface TaskSummaryProps {
    selectedTask: ExecutionTask;
    onClose: () => void;
}
export declare const SummaryTask: FunctionComponent<TaskSummaryProps>;
export {};
