import { ExecutionTask } from "types/Execution";
export declare function taskIdRenderer(handleClick: (row: ExecutionTask) => void): (taskId: string, row: ExecutionTask) => import("react").JSX.Element;
export declare function clickHandler(handleSelectedTask: ((task: ExecutionTask) => void) | undefined): (row: ExecutionTask) => void;
