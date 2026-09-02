import { BuildQueryOutput } from "../ApiSearchModalIntegration";
interface TaskApiSearchModalProps {
    buildQueryOutput: BuildQueryOutput;
    onClose: () => void;
}
export declare const TaskApiSearchModal: ({ onClose, buildQueryOutput, }: TaskApiSearchModalProps) => import("react").JSX.Element;
export {};
