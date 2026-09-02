import { ExecutionTask } from "types/Execution";
import "./timeline.scss";
type ExecutionStatusMap = Record<string, {
    related?: unknown;
}>;
interface TimelineComponentProps {
    tasks: ExecutionTask[];
    onClick: (task: {
        ref: string;
        taskId: string;
    }) => void;
    selectedTask?: {
        taskId?: string;
    } | null;
    executionStatusMap?: ExecutionStatusMap;
}
export default function TimelineComponent({ tasks, onClick, selectedTask, executionStatusMap, }: TimelineComponentProps): import("react").JSX.Element;
export {};
