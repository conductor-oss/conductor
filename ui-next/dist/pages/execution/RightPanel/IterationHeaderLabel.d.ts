import { TaskStatus } from "types/TaskStatus";
interface IterationHeaderLabelProps {
    status: TaskStatus;
    text: string;
}
/**
 * Accordion header label used by both DoWhileIteration and
 * InlineTaskIterations: a small status icon followed by a text label.
 */
export declare function IterationHeaderLabel({ status, text, }: IterationHeaderLabelProps): import("react").JSX.Element;
export {};
