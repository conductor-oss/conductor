import { TaskAndCrumbs } from "pages/definition/state/usePerformOperationOnDefintion";
import { ReactNode } from "react";
interface AddPathButtonProps {
    children: ReactNode;
    nodeData: TaskAndCrumbs;
}
declare const AddPathButton: ({ children, nodeData }: AddPathButtonProps) => import("react").JSX.Element;
export default AddPathButton;
