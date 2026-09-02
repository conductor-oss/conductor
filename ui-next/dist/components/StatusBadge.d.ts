import { FunctionComponent } from "react";
import { TaskStatus } from "types/TaskStatus";
import { HumanTaskState as TaskState } from "types/HumanTaskTypes";
export interface StatusBadgeProps {
    status: TaskStatus | TaskState;
    labelConcat?: string;
}
declare const StatusBadge: FunctionComponent<StatusBadgeProps>;
export default StatusBadge;
