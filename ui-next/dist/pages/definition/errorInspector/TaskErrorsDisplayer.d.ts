import { FunctionComponent } from "react";
import { TaskErrors } from "./state/types";
interface TaskErrorsDisplayerProps {
    taskErrors: TaskErrors[];
    expanded: boolean;
    onToggleExpand: () => void;
    title?: string;
    onClickReference?: (data: string) => void;
}
export declare const TaskErrorsDisplayer: FunctionComponent<TaskErrorsDisplayerProps>;
export {};
